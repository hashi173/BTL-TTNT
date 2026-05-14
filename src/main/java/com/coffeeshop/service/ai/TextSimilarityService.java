package com.coffeeshop.service.ai;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

/**
 * Service so sánh văn bản sử dụng TF-IDF và Cosine Similarity.
 *
 * ═══════════════════════════════════════════════════════════════
 * THUẬT TOÁN: TF-IDF (Term Frequency - Inverse Document Frequency)
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. TF (Term Frequency) - Tần suất thuật ngữ:
 *    TF(t, d) = số lần xuất hiện của từ t trong tài liệu d / tổng số từ trong d
 *    → Đo lường mức độ quan trọng của 1 từ trong 1 tài liệu cụ thể
 *
 * 2. IDF (Inverse Document Frequency) - Nghịch đảo tần suất tài liệu:
 *    IDF(t) = log(Tổng số tài liệu / Số tài liệu chứa từ t)
 *    → Giảm trọng số của những từ xuất hiện ở nhiều tài liệu (ví dụ: "và", "là", "của")
 *
 * 3. TF-IDF(t, d) = TF(t, d) × IDF(t)
 *    → Điểm càng cao → từ đó càng quan trọng và đặc trưng cho tài liệu đó
 *
 * ═══════════════════════════════════════════════════════════════
 * THUẬT TOÁN: Cosine Similarity
 * ═══════════════════════════════════════════════════════════════
 *
 * cosine_sim(A, B) = (A · B) / (|A| × |B|)
 *
 * Trong đó:
 *   A · B = Σ(Ai × Bi)         (tích vô hướng)
 *   |A| = √(Σ Ai²)            (độ dài vector A)
 *   |B| = √(Σ Bi²)            (độ dài vector B)
 *
 * Kết quả: giá trị từ 0 đến 1
 *   1 = hoàn toàn giống nhau
 *   0 = hoàn toàn khác nhau
 */
@Service
public class TextSimilarityService {

    // Ngưỡng tối thiểu để coi là match (cosine similarity >= threshold)
    private static final double MATCH_THRESHOLD = 0.1;

    /**
     * Stopwords tiếng Việt và tiếng Anh - các từ xuất hiện quá phổ biến,
     * không mang ý nghĩa phân biệt, cần loại bỏ trước khi tính TF-IDF.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "la", "va", "cua", "cho", "duoc", "mot", "nhung", "cac", "voi",
            "tai", "tu", "den", "hay", "hoac", "neu", "vi", "nhu",
            "nay", "no", "minh", "toi", "ban", "chung", "ho", "ta",
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "shall",
            "and", "or", "but", "in", "on", "at", "to", "for", "of",
            "with", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "up",
            "down", "it", "its", "this", "that", "these", "those",
            "i", "me", "my", "we", "our", "you", "your", "he", "him",
            "his", "she", "her", "they", "them", "their", "what", "which",
            "who", "whom", "when", "where", "why", "how", "all", "each",
            "every", "both", "few", "more", "most", "other", "some", "such",
            "not", "only", "own", "same", "so", "than", "too", "very",
            "mon", "nuoc", "gia", "tieng", "cung", "dua", "com"
    );

    /**
     * Tính Cosine Similarity giữa 2 chuỗi văn bản.
     *
     * Quy trình:
     * 1. Normalize text (loại bỏ dấu, lowercase)
     * 2. Tokenize (tách từ)
     * 3. Loại bỏ stopwords
     * 4. Xây dựng vocabulary chung
     * 5. Tính TF-IDF vector cho mỗi chuỗi
     * 6. Tính cosine similarity giữa 2 vector
     *
     * @param text1 Văn bản thứ nhất
     * @param text2 Văn bản thứ hai
     * @return Giá trị cosine similarity từ 0.0 đến 1.0
     */
    public double computeCosineSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;

        // Bước 1 & 2: Normalize và tokenize
        List<String> tokens1 = tokenize(text1);
        List<String> tokens2 = tokenize(text2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0;

        // Bước 3: Xây dựng vocabulary (tập hợp tất cả các từ duy nhất)
        Set<String> vocabulary = new HashSet<>();
        vocabulary.addAll(tokens1);
        vocabulary.addAll(tokens2);

        // Bước 4: Tính TF cho mỗi văn bản
        Map<String, Double> tf1 = computeTF(tokens1);
        Map<String, Double> tf2 = computeTF(tokens2);

        // Bước 5: Tính IDF dựa trên cả 2 "tài liệu"
        Map<String, Double> idf = computeIDF(tokens1, tokens2, vocabulary);

        // Bước 6: Tính TF-IDF vector
        Map<String, Double> tfidf1 = computeTFIDF(tf1, idf, vocabulary);
        Map<String, Double> tfidf2 = computeTFIDF(tf2, idf, vocabulary);

        // Bước 7: Tính cosine similarity giữa 2 vector TF-IDF
        return cosineSimilarity(tfidf1, tfidf2, vocabulary);
    }

    /**
     * Kiểm tra 2 chuỗi có liên quan đến nhau không (vượt ngưỡng similarity).
     */
    public boolean isRelevant(String text1, String text2) {
        return computeCosineSimilarity(text1, text2) >= MATCH_THRESHOLD;
    }

    /**
     * Tìm sản phẩm liên quan nhất từ danh sách, dựa trên cosine similarity
     * giữa query và tên + mô tả sản phẩm.
     *
     * @param query      Câu truy vấn của user
     * @param products   Danh sách sản phẩm dạng [name, description]
     * @param topK       Số kết quả trả về
     * @return Danh sách index của sản phẩm được sắp xếp theo relevance giảm dần
     */
    public List<Integer> findRelevantProducts(String query, List<String[]> products, int topK) {
        // Tính TF-IDF cho toàn bộ corpus (query + tất cả sản phẩm)
        List<String> corpus = new ArrayList<>();
        corpus.add(normalizeText(query));
        for (String[] product : products) {
            String combined = (product[0] != null ? product[0] : "") + " " + (product[1] != null ? product[1] : "");
            corpus.add(normalizeText(combined));
        }

        // Tokenize corpus
        List<List<String>> tokenizedCorpus = new ArrayList<>();
        for (String doc : corpus) {
            tokenizedCorpus.add(tokenizeRaw(doc)); // dùng tokenizeRaw vì đã normalize rồi
        }

        // Xây dựng vocabulary
        Set<String> vocabulary = new HashSet<>();
        for (List<String> tokens : tokenizedCorpus) {
            vocabulary.addAll(tokens);
        }

        // Tính IDF cho toàn bộ corpus
        Map<String, Double> idf = computeIDFCorpus(tokenizedCorpus, vocabulary);

        // Tính TF-IDF vector cho query (index 0)
        Map<String, Double> queryTF = computeTF(tokenizedCorpus.get(0));
        Map<String, Double> queryTFIDF = computeTFIDF(queryTF, idf, vocabulary);

        // Tính similarity cho từng sản phẩm
        List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
        for (int i = 1; i < tokenizedCorpus.size(); i++) {
            Map<String, Double> docTF = computeTF(tokenizedCorpus.get(i));
            Map<String, Double> docTFIDF = computeTFIDF(docTF, idf, vocabulary);
            double sim = cosineSimilarity(queryTFIDF, docTFIDF, vocabulary);
            scored.add(Map.entry(i - 1, sim)); // i-1 vì index 0 là query
        }

        // Sắp xếp giảm dần theo similarity
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return scored.stream()
                .limit(topK)
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ═══════════════════════════════════════════════════════
    // CÁC HÀM HỖ TRỢ (HELPER METHODS)
    // ═══════════════════════════════════════════════════════

    /**
     * Normalize text: loại bỏ dấu tiếng Việt, chuyển lowercase, loại ký tự đặc biệt.
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")    // Loại bỏ combining marks (dấu)
                .toLowerCase()
                .replace("đ", "d")
                .replaceAll("[^a-z0-9\\s]", " ") // Chỉ giữ chữ và số
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Tokenize: normalize + tách từ + loại bỏ stopwords.
     */
    private List<String> tokenize(String text) {
        String normalized = normalizeText(text);
        return tokenizeRaw(normalized);
    }

    /**
     * Tokenize từ text đã normalize: tách từ + loại bỏ stopwords.
     */
    private List<String> tokenizeRaw(String normalizedText) {
        String[] words = normalizedText.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String word : words) {
            if (word.length() >= 2 && !STOPWORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * Tính Term Frequency (TF) cho một danh sách tokens.
     *
     * Công thức: TF(t) = (số lần t xuất hiện) / (tổng số token)
     *
     * @return Map<token, TF value>
     */
    private Map<String, Double> computeTF(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;

        // Đếm tần suất xuất hiện
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }

        // Chuẩn hóa bằng tổng số token
        double total = tokens.size();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            tf.put(entry.getKey(), entry.getValue() / total);
        }
        return tf;
    }

    /**
     * Tính IDF cho 2 "tài liệu" (query + 1 product text).
     *
     * Công thức: IDF(t) = log(N / df(t))
     *   N = tổng số tài liệu (ở đây = 2)
     *   df(t) = số tài liệu chứa từ t
     */
    private Map<String, Double> computeIDF(List<String> tokens1, List<String> tokens2, Set<String> vocabulary) {
        Map<String, Double> idf = new HashMap<>();
        Set<String> set1 = new HashSet<>(tokens1);
        Set<String> set2 = new HashSet<>(tokens2);
        int n = 2; // tổng số tài liệu

        for (String term : vocabulary) {
            int docFreq = 0;
            if (set1.contains(term)) docFreq++;
            if (set2.contains(term)) docFreq++;
            // Sử dụng smoothed IDF: log(N / (1 + df)) + 1 để tránh chia cho 0
            idf.put(term, Math.log(n / (1.0 + docFreq)) + 1.0);
        }
        return idf;
    }

    /**
     * Tính IDF cho toàn bộ corpus (nhiều tài liệu).
     */
    private Map<String, Double> computeIDFCorpus(List<List<String>> corpus, Set<String> vocabulary) {
        Map<String, Double> idf = new HashMap<>();
        int n = corpus.size();

        for (String term : vocabulary) {
            int docFreq = 0;
            for (List<String> doc : corpus) {
                if (doc.contains(term)) docFreq++;
            }
            idf.put(term, Math.log((double) n / (1.0 + docFreq)) + 1.0);
        }
        return idf;
    }

    /**
     * Tính TF-IDF vector: TF-IDF(t) = TF(t) × IDF(t)
     */
    private Map<String, Double> computeTFIDF(Map<String, Double> tf, Map<String, Double> idf, Set<String> vocabulary) {
        Map<String, Double> tfidf = new HashMap<>();
        for (String term : vocabulary) {
            double tfVal = tf.getOrDefault(term, 0.0);
            double idfVal = idf.getOrDefault(term, 1.0);
            tfidf.put(term, tfVal * idfVal);
        }
        return tfidf;
    }

    /**
     * Tính Cosine Similarity giữa 2 vector TF-IDF.
     *
     * Công thức: cos(θ) = (A · B) / (|A| × |B|)
     *   A · B = Σ(Ai × Bi)     → tích vô hướng
     *   |A| = √(Σ Ai²)        → độ dài (L2 norm)
     *
     * @return Giá trị từ 0.0 (hoàn toàn khác) đến 1.0 (hoàn toàn giống)
     */
    private double cosineSimilarity(Map<String, Double> vec1, Map<String, Double> vec2, Set<String> vocabulary) {
        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        for (String term : vocabulary) {
            double v1 = vec1.getOrDefault(term, 0.0);
            double v2 = vec2.getOrDefault(term, 0.0);
            dotProduct += v1 * v2;
            magnitude1 += v1 * v1;
            magnitude2 += v2 * v2;
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0.0 || magnitude2 == 0.0) return 0.0;
        return dotProduct / (magnitude1 * magnitude2);
    }
}
