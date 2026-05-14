package com.coffeeshop.service.ai;

import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bộ phân loại Naive Bayes cho việc dự đoán danh mục sản phẩm từ văn bản.
 *
 * ═══════════════════════════════════════════════════════════════
 * THUẬT TOÁN: Naive Bayes Classifier
 * ═══════════════════════════════════════════════════════════════
 *
 * Naive Bayes dựa trên định lý Bayes:
 *
 *   P(Class | Features) = P(Features | Class) × P(Class)
 *                         ─────────────────────────────────
 *                                  P(Features)
 *
 * Trong đó:
 *   P(Class | Features) = xác suất hậu nghiệm (posterior)
 *   P(Features | Class) = likelihood (xác suất đặc trưng theo lớp)
 *   P(Class)            = prior (xác suất tiên nghiệm của lớp)
 *   P(Features)         = evidence (hằng số chuẩn hóa)
 *
 * Giả định "Naive": Các đặc trưng (từ) ĐỘC LẬP với nhau khi cho trước lớp.
 * → P(f1,f2,...,fn | C) = P(f1|C) × P(f2|C) × ... × P(fn|C)
 *
 * Với Laplace Smoothing để tránh xác suất bằng 0:
 *   P(word | class) = (count(word, class) + α) / (total_words_in_class + α × |vocabulary|)
 *   với α = 1 (Laplace smoothing)
 *
 * Ưu điểm:
 * - Nhanh, đơn giản, hiệu quả với dữ liệu nhỏ
 * - Hoạt động tốt cho phân loại văn bản (text classification)
 * - Không cần training phức tạp
 *
 * Nhược điểm:
 * - Giả định independence hiếm khi đúng trong thực tế
 * - Không capture được mối quan hệ giữa các từ
 */
@Service
@RequiredArgsConstructor
public class NaiveBayesClassifier {

    private final ProductRepository productRepository;

    // ═══════════════════════════════════════════════════════
    // CẤU TRÚC DỮ LIỆU CHO NAIVE BAYES
    // ═══════════════════════════════════════════════════════

    /**
     * Mô hình đã học (trained model).
     * Được xây dựng từ dữ liệu sản phẩm trong database.
     */
    private volatile Map<String, CategoryModel> models;
    private volatile boolean trained = false;

    /**
     * Mô hình cho 1 category, chứa:
     * - prior: log P(category) - xác suất tiên nghiệm
     * - wordLogProbs: log P(word | category) cho mỗi từ
     * - totalWords: tổng số từ trong category (dùng cho smoothing)
     */
    private static class CategoryModel {
        final double logPrior;                    // log P(category)
        final Map<String, Double> wordLogProbs;   // log P(word | category)
        final int totalWords;                     // tổng số từ

        CategoryModel(double logPrior, Map<String, Double> wordLogProbs, int totalWords) {
            this.logPrior = logPrior;
            this.wordLogProbs = wordLogProbs;
            this.totalWords = totalWords;
        }
    }

    // ═══════════════════════════════════════════════════════
    // STOPWORDS
    // ═══════════════════════════════════════════════════════

    private static final Set<String> STOPWORDS = Set.of(
            "la", "va", "cua", "cho", "duoc", "mot", "nhung", "cac", "voi",
            "tai", "tu", "den", "hay", "hoac", "neu", "vi", "nhu",
            "the", "a", "an", "is", "are", "was", "and", "or", "but",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "it", "this", "that", "i", "me", "my", "we", "you", "he",
            "she", "they", "not", "no", "so", "very", "can", "will"
    );

    // ═══════════════════════════════════════════════════════
    // TRAINING
    // ═══════════════════════════════════════════════════════

    /**
     * Huấn luyện mô hình Naive Bayes từ dữ liệu sản phẩm trong database.
     *
     * Quy trình:
     * 1. Lấy tất cả sản phẩm, nhóm theo category
     * 2. Với mỗi category, tokenize tên + mô tả → tạo vocabulary
     * 3. Tính P(category) = |sản phẩm trong category| / |tổng sản phẩm|
     * 4. Tính P(word | category) với Laplace smoothing
     */
    public synchronized void train() {
        if (trained) return;

        List<Product> products = productRepository.findAllWithDetails();
        if (products.isEmpty()) {
            models = Collections.emptyMap();
            trained = true;
            return;
        }

        // Nhóm sản phẩm theo category
        Map<String, List<Product>> byCategory = new HashMap<>();
        for (Product p : products) {
            String catName = p.getCategory() != null ? p.getCategory().getName() : "Other";
            byCategory.computeIfAbsent(catName, k -> new ArrayList<>()).add(p);
        }

        int totalProducts = products.size();
        Map<String, CategoryModel> categoryModels = new HashMap<>();

        // Xây dựng vocabulary chung (để tính Laplace smoothing)
        Set<String> globalVocabulary = new HashSet<>();
        Map<String, List<String>> categoryTokens = new HashMap<>();

        for (Map.Entry<String, List<Product>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<String> tokens = new ArrayList<>();
            for (Product p : entry.getValue()) {
                String text = (p.getName() != null ? p.getName() : "") + " "
                        + (p.getDescription() != null ? p.getDescription() : "");
                tokens.addAll(tokenize(text));
            }
            categoryTokens.put(category, tokens);
            globalVocabulary.addAll(tokens);
        }

        int vocabSize = globalVocabulary.size();

        // Tính mô hình cho mỗi category
        for (Map.Entry<String, List<String>> entry : categoryTokens.entrySet()) {
            String category = entry.getKey();
            List<String> tokens = entry.getValue();
            int categoryProductCount = byCategory.get(category).size();

            // Prior: log P(category) = log(|products in category| / |total products|)
            double logPrior = Math.log((double) categoryProductCount / totalProducts);

            // Đếm tần suất từ trong category
            Map<String, Integer> wordCounts = new HashMap<>();
            for (String token : tokens) {
                wordCounts.merge(token, 1, Integer::sum);
            }

            // Tính log P(word | category) với Laplace Smoothing
            // P(word | class) = (count(word, class) + 1) / (total_words + vocab_size)
            Map<String, Double> wordLogProbs = new HashMap<>();
            double denominator = tokens.size() + vocabSize; // tổng từ + |V|

            for (String word : globalVocabulary) {
                int count = wordCounts.getOrDefault(word, 0);
                // Laplace smoothing: +1 ở tử số
                double prob = (count + 1.0) / denominator;
                wordLogProbs.put(word, Math.log(prob));
            }

            // Log probability cho từ chưa gặp (out-of-vocabulary)
            // P(OOV | class) = 1 / (total_words + vocab_size)
            double oovLogProb = Math.log(1.0 / denominator);

            categoryModels.put(category, new CategoryModel(logPrior, wordLogProbs, tokens.size()));
        }

        models = categoryModels;
        trained = true;
    }

    // ═══════════════════════════════════════════════════════
    // PREDICTION
    // ═══════════════════════════════════════════════════════

    /**
     * Dự đoán category cho một chuỗi văn bản đầu vào.
     *
     * Thuật toán:
     * 1. Tokenize input text
     * 2. Với mỗi category, tính score = log P(category) + Σ log P(word | category)
     * 3. Chọn category có score cao nhất (MAP - Maximum A Posteriori)
     *
     * @param text Văn bản đầu vào (ví dụ: "cà phê sữa đá")
     * @return Tên category được dự đoán
     */
    public String predict(String text) {
        ensureTrained();

        if (models == null || models.isEmpty()) return "Other";

        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return models.keySet().iterator().next();

        String bestCategory = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Map.Entry<String, CategoryModel> entry : models.entrySet()) {
            String category = entry.getKey();
            CategoryModel model = entry.getValue();

            // Score = log P(category) + Σ log P(word | category)
            double score = model.logPrior;
            for (String token : tokens) {
                Double logProb = model.wordLogProbs.get(token);
                if (logProb != null) {
                    score += logProb;
                }
                // Nếu từ không có trong vocabulary → bỏ qua (không cộng gì)
                // Đây là simplified version, thực tế có thể dùng OOV probability
            }

            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }

        return bestCategory;
    }

    /**
     * Dự đoán và trả về xác suất cho TẤT CẢ category (để hiển thị confidence).
     *
     * Sử dụng softmax để chuyển log-scores thành xác suất:
     *   P(class_i) = exp(score_i) / Σ exp(score_j)
     *
     * @param text Văn bản đầu vào
     * @return Map<Category, Probability> sắp xếp giảm dần
     */
    public Map<String, Double> predictProbabilities(String text) {
        ensureTrained();

        if (models == null || models.isEmpty()) return Collections.emptyMap();

        List<String> tokens = tokenize(text);
        Map<String, Double> scores = new HashMap<>();

        // Tính log-score cho mỗi category
        for (Map.Entry<String, CategoryModel> entry : models.entrySet()) {
            CategoryModel model = entry.getValue();
            double score = model.logPrior;
            for (String token : tokens) {
                Double logProb = model.wordLogProbs.get(token);
                if (logProb != null) score += logProb;
            }
            scores.put(entry.getKey(), score);
        }

        // Softmax: chuyển log-scores thành xác suất
        // Để tránh overflow, trừ max score trước
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Map<String, Double> probabilities = new HashMap<>();
        double sumExp = 0.0;

        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            double exp = Math.exp(entry.getValue() - maxScore);
            probabilities.put(entry.getKey(), exp);
            sumExp += exp;
        }

        // Chuẩn hóa: mỗi xác suất chia cho tổng
        final double totalExp = sumExp;
        probabilities.replaceAll((k, v) -> v / totalExp);

        // Sắp xếp giảm dần
        return probabilities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    /**
     * Lấy danh sách category keywords để hiển thị trên AI dashboard.
     */
    public Map<String, List<String>> getCategoryKeywords() {
        ensureTrained();
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (models == null) return result;

        for (Map.Entry<String, CategoryModel> entry : models.entrySet()) {
            // Lấy top 10 từ có log-probability cao nhất (đặc trưng nhất)
            List<String> topWords = entry.getValue().wordLogProbs.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();
            result.put(entry.getKey(), topWords);
        }
        return result;
    }

    private void ensureTrained() {
        if (!trained) train();
    }

    /**
     * Tokenize: normalize + tách từ + loại bỏ stopwords.
     */
    private List<String> tokenize(String text) {
        if (text == null) return List.of();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replace("đ", "d")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String word : words) {
            if (word.length() >= 2 && !STOPWORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }
}
