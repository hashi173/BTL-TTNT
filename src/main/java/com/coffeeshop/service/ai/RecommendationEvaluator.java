package com.coffeeshop.service.ai;

import com.coffeeshop.entity.OrderItem;
import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service đánh giá hiệu suất hệ thống gợi ý sản phẩm.
 *
 * ═══════════════════════════════════════════════════════════════
 * CÁC CHỈ SỐ ĐÁNH GIÁ (EVALUATION METRICS)
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Precision@K = |recommended ∩ relevant| / K
 * 2. Recall@K    = |recommended ∩ relevant| / |relevant|
 * 3. F1-Score    = 2 × (P × R) / (P + R)
 * 4. Hit Rate    = users with ≥1 hit / total users
 * 5. MAP@K       = mean of 1/rank for each hit
 *
 * ═══════════════════════════════════════════════════════════════
 * PHƯƠNG PHÁP: Leave-One-Out Cross-Validation
 * ═══════════════════════════════════════════════════════════════
 * Với mỗi user có ≥2 sản phẩm: ẩn 1, gợi ý bằng phần còn lại,
 * kiểm tra test item có trong top-K không.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEvaluator {

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final BaselineRecommender baselineRecommender;
    private final SyntheticDataGenerator syntheticDataGenerator;

    private static final int DEFAULT_K = 6;

    // ═══════════════════════════════════════════════════════
    // EVALUATION RESULT
    // ═══════════════════════════════════════════════════════

    public static class EvaluationResult {
        private final double precision;
        private final double recall;
        private final double f1Score;
        private final double hitRate;
        private final double map;
        private final int totalUsers;
        private final int evaluatedUsers;

        public EvaluationResult(double precision, double recall, double f1Score,
                double hitRate, double map, int totalUsers, int evaluatedUsers) {
            this.precision = precision;
            this.recall = recall;
            this.f1Score = f1Score;
            this.hitRate = hitRate;
            this.map = map;
            this.totalUsers = totalUsers;
            this.evaluatedUsers = evaluatedUsers;
        }

        public double getPrecision() { return precision; }
        public double getRecall() { return recall; }
        public double getF1Score() { return f1Score; }
        public double getHitRate() { return hitRate; }
        public double getMap() { return map; }
        public int getTotalUsers() { return totalUsers; }
        public int getEvaluatedUsers() { return evaluatedUsers; }

        @Override
        public String toString() {
            return String.format("K=%d users=%d/%d P=%.4f R=%.4f F1=%.4f HR=%.4f MAP=%.4f",
                    DEFAULT_K, evaluatedUsers, totalUsers, precision, recall, f1Score, hitRate, map);
        }
    }

    // ═══════════════════════════════════════════════════════
    // CONFUSION MATRIX RESULT
    // ═══════════════════════════════════════════════════════

    public static class ConfusionMatrixResult {
        private final Map<String, Map<String, Integer>> matrix;
        private final Map<String, Double> precisionPerClass;
        private final Map<String, Double> recallPerClass;
        private final Map<String, Double> f1PerClass;
        private final double accuracy;
        private final int totalSamples;

        public ConfusionMatrixResult(Map<String, Map<String, Integer>> matrix,
                Map<String, Double> precisionPerClass, Map<String, Double> recallPerClass,
                Map<String, Double> f1PerClass, double accuracy, int totalSamples) {
            this.matrix = matrix;
            this.precisionPerClass = precisionPerClass;
            this.recallPerClass = recallPerClass;
            this.f1PerClass = f1PerClass;
            this.accuracy = accuracy;
            this.totalSamples = totalSamples;
        }

        public Map<String, Map<String, Integer>> getMatrix() { return matrix; }
        public Map<String, Double> getPrecisionPerClass() { return precisionPerClass; }
        public Map<String, Double> getRecallPerClass() { return recallPerClass; }
        public Map<String, Double> getF1PerClass() { return f1PerClass; }
        public double getAccuracy() { return accuracy; }
        public int getTotalSamples() { return totalSamples; }
    }

    // ═══════════════════════════════════════════════════════
    // BASIC EVALUATION (LOO)
    // ═══════════════════════════════════════════════════════

    public EvaluationResult evaluate(int k) {
        log.info("Starting recommendation evaluation with K={}", k);

        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) {
            return new EvaluationResult(0, 0, 0, 0, 0, 0, 0);
        }

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return new EvaluationResult(0, 0, 0, 0, 0, userPurchases.size(), 0);
        }

        return evaluateLOO(userIds, userPurchases, userProductScores, k);
    }

    public EvaluationResult evaluate() {
        return evaluate(DEFAULT_K);
    }

    // ═══════════════════════════════════════════════════════
    // ABLATION STUDY: CF/RB WEIGHT SWEEP
    // ═══════════════════════════════════════════════════════

    /**
     * Thử nghiệm nhiều cặp trọng số CF/RB để tìm optimal weights.
     * Test: (0.9,0.1), (0.8,0.2), ..., (0.1,0.9)
     */
    public Map<String, EvaluationResult> ablationStudyWeights() {
        log.info("Starting ablation study: CF/RB weight sweep");

        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) return Collections.emptyMap();

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Collections.emptyMap();

        // Tính popularity cho rule-based
        Map<UUID, Double> globalPopularity = computeGlobalPopularity(allItems);

        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        double[] cfWeights = {0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1};

        for (double cfW : cfWeights) {
            double rbW = 1.0 - cfW;
            EvaluationResult result = evaluateWithWeights(
                    userIds, userPurchases, userProductScores, globalPopularity, cfW, rbW, DEFAULT_K);
            String key = String.format("%.1f/%.1f", cfW, rbW);
            results.put(key, result);
            log.info("Weight {}: {}", key, result);
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════
    // ABLATION STUDY: K SWEEP
    // ═══════════════════════════════════════════════════════

    /**
     * Thử nghiệm nhiều giá trị K để tìm optimal top-K.
     */
    public Map<Integer, EvaluationResult> ablationStudyK() {
        log.info("Starting ablation study: K sweep");

        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) return Collections.emptyMap();

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Collections.emptyMap();

        Map<Integer, EvaluationResult> results = new LinkedHashMap<>();
        int[] kValues = {1, 2, 3, 4, 5, 6, 8, 10, 15, 20};

        for (int k : kValues) {
            EvaluationResult result = evaluateLOO(userIds, userPurchases, userProductScores, k);
            results.put(k, result);
            log.info("K={}: {}", k, result);
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════
    // BASELINE COMPARISON
    // ═══════════════════════════════════════════════════════

    /**
     * So sánh Hybrid vs CF-only vs RB-only vs Random vs Popularity.
     */
    public Map<String, EvaluationResult> compareBaselines() {
        log.info("Starting baseline comparison");

        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) return Collections.emptyMap();

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Collections.emptyMap();

        Map<UUID, Double> globalPopularity = computeGlobalPopularity(allItems);
        Map<String, EvaluationResult> results = new LinkedHashMap<>();

        // 1. Hybrid (0.6 CF + 0.4 RB)
        results.put("Hybrid (0.6/0.4)", evaluateWithWeights(
                userIds, userPurchases, userProductScores, globalPopularity, 0.6, 0.4, DEFAULT_K));

        // 2. CF-only (1.0/0.0)
        results.put("CF-Only", evaluateWithWeights(
                userIds, userPurchases, userProductScores, globalPopularity, 1.0, 0.0, DEFAULT_K));

        // 3. RB-only (0.0/1.0)
        results.put("RB-Only", evaluateWithWeights(
                userIds, userPurchases, userProductScores, globalPopularity, 0.0, 1.0, DEFAULT_K));

        // 4. Random baseline
        double randomHitRate = baselineRecommender.evaluateRandomLOO(
                userProductScores, globalPopularity.keySet(), DEFAULT_K);
        results.put("Random", new EvaluationResult(
                randomHitRate / DEFAULT_K, randomHitRate, 0, randomHitRate, 0,
                userProductScores.size(), userIds.size()));

        // 5. Popularity baseline
        double popHitRate = baselineRecommender.evaluatePopularityLOO(
                userProductScores, globalPopularity, DEFAULT_K);
        results.put("Popularity", new EvaluationResult(
                popHitRate / DEFAULT_K, popHitRate, 0, popHitRate, 0,
                userProductScores.size(), userIds.size()));

        return results;
    }

    // ═══════════════════════════════════════════════════════
    // NAIVE BAYES CONFUSION MATRIX
    // ═══════════════════════════════════════════════════════

    /**
     * Đánh giá Naive Bayes: confusion matrix + per-class metrics.
     * Dùng chính sản phẩm trong DB làm test set.
     */
    public ConfusionMatrixResult evaluateNaiveBayes(NaiveBayesClassifier classifier) {
        List<Product> products = productRepository.findAllWithDetails();
        if (products.isEmpty()) {
            return new ConfusionMatrixResult(Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0);
        }

        // Lấy tất cả category names
        Set<String> categories = products.stream()
                .map(p -> p.getCategory() != null ? p.getCategory().getName() : "Other")
                .collect(Collectors.toCollection(TreeSet::new));

        // Khởi tạo confusion matrix
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String actual : categories) {
            matrix.put(actual, new LinkedHashMap<>());
            for (String predicted : categories) {
                matrix.get(actual).put(predicted, 0);
            }
        }

        // Dự đoán từng sản phẩm
        int correct = 0;
        for (Product p : products) {
            String actual = p.getCategory() != null ? p.getCategory().getName() : "Other";
            String text = (p.getName() != null ? p.getName() : "") + " "
                    + (p.getDescription() != null ? p.getDescription() : "");
            String predicted = classifier.predict(text);

            matrix.get(actual).merge(predicted, 1, Integer::sum);
            if (actual.equals(predicted)) correct++;
        }

        double accuracy = products.isEmpty() ? 0 : (double) correct / products.size();

        // Per-class metrics
        Map<String, Double> precisionPerClass = new LinkedHashMap<>();
        Map<String, Double> recallPerClass = new LinkedHashMap<>();
        Map<String, Double> f1PerClass = new LinkedHashMap<>();

        for (String cat : categories) {
            // TP = matrix[cat][cat]
            int tp = matrix.get(cat).getOrDefault(cat, 0);

            // FP = sum of matrix[other][cat] for all other != cat
            int fp = 0;
            for (String other : categories) {
                if (!other.equals(cat)) {
                    fp += matrix.get(other).getOrDefault(cat, 0);
                }
            }

            // FN = sum of matrix[cat][other] for all other != cat
            int fn = 0;
            for (String other : categories) {
                if (!other.equals(cat)) {
                    fn += matrix.get(cat).getOrDefault(other, 0);
                }
            }

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

            precisionPerClass.put(cat, precision);
            recallPerClass.put(cat, recall);
            f1PerClass.put(cat, f1);
        }

        return new ConfusionMatrixResult(matrix, precisionPerClass, recallPerClass,
                f1PerClass, accuracy, products.size());
    }

    // ═══════════════════════════════════════════════════════
    // PRECISION-RECALL CURVE
    // ═══════════════════════════════════════════════════════

    /**
     * Dữ liệu cho Precision-Recall curve.
     * Vary K từ 1 đến 20, compute P và R tại mỗi K.
     */
    public List<Map<String, Object>> precisionRecallCurve() {
        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) return Collections.emptyList();

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> curve = new ArrayList<>();
        for (int k = 1; k <= 20; k++) {
            EvaluationResult r = evaluateLOO(userIds, userPurchases, userProductScores, k);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("k", k);
            point.put("precision", Math.round(r.getPrecision() * 10000.0) / 10000.0);
            point.put("recall", Math.round(r.getRecall() * 10000.0) / 10000.0);
            curve.add(point);
        }
        return curve;
    }

    // ═══════════════════════════════════════════════════════
    // F1 BY K CURVE
    // ═══════════════════════════════════════════════════════

    /**
     * Dữ liệu cho F1@K curve. K từ 1 đến 20.
     */
    public List<Map<String, Object>> f1ByKCurve() {
        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();
        if (allItems.isEmpty()) return Collections.emptyList();

        Map<UUID, Map<UUID, Double>> userProductScores = buildUserProductMatrix(allItems);
        Map<UUID, Set<UUID>> userPurchases = new HashMap<>();
        for (var entry : userProductScores.entrySet()) {
            userPurchases.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }

        List<UUID> userIds = userPurchases.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> curve = new ArrayList<>();
        for (int k = 1; k <= 20; k++) {
            EvaluationResult r = evaluateLOO(userIds, userPurchases, userProductScores, k);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("k", k);
            point.put("f1", Math.round(r.getF1Score() * 10000.0) / 10000.0);
            point.put("precision", Math.round(r.getPrecision() * 10000.0) / 10000.0);
            point.put("recall", Math.round(r.getRecall() * 10000.0) / 10000.0);
            point.put("hitRate", Math.round(r.getHitRate() * 10000.0) / 10000.0);
            curve.add(point);
        }
        return curve;
    }

    // ═══════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════

    private Map<UUID, Map<UUID, Double>> buildUserProductMatrix(List<OrderItem> allItems) {
        Map<UUID, Map<UUID, Double>> matrix = new HashMap<>();

        for (OrderItem item : allItems) {
            if (item.getOrder() == null || item.getOrder().getUser() == null
                    || item.getProduct() == null) continue;

            UUID userId = item.getOrder().getUser().getId();
            UUID productId = item.getProduct().getId();
            double quantity = item.getQuantity() != null ? item.getQuantity() : 1;

            matrix.computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(productId, quantity, Double::sum);
        }

        // Normalize
        for (Map<UUID, Double> scores : matrix.values()) {
            double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            if (max > 0) scores.replaceAll((k, v) -> v / max);
        }

        return matrix;
    }

    private Map<UUID, Double> computeGlobalPopularity(List<OrderItem> allItems) {
        Map<UUID, Double> popularity = new HashMap<>();
        for (OrderItem item : allItems) {
            if (item.getProduct() == null) continue;
            double qty = item.getQuantity() != null ? item.getQuantity() : 1;
            popularity.merge(item.getProduct().getId(), qty, Double::sum);
        }
        double max = popularity.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max > 0) popularity.replaceAll((k, v) -> v / max);
        return popularity;
    }

    /**
     * LOO evaluation trên ma trận cho trước.
     */
    private EvaluationResult evaluateLOO(List<UUID> userIds,
            Map<UUID, Set<UUID>> userPurchases,
            Map<UUID, Map<UUID, Double>> userProductScores, int k) {

        double totalPrecision = 0, totalRecall = 0, totalF1 = 0, totalAP = 0;
        int hitCount = 0;

        for (UUID userId : userIds) {
            Set<UUID> allPurchased = userPurchases.get(userId);
            Map<UUID, Double> userScores = userProductScores.get(userId);

            UUID testProductId = userScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (testProductId == null) continue;

            Map<UUID, Double> recommendationScores = computeCFOnlyScores(userId, userScores, userProductScores, allPurchased);

            List<UUID> topK = recommendationScores.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .limit(k).map(Map.Entry::getKey).collect(Collectors.toList());

            boolean hit = topK.contains(testProductId);
            double precision = hit ? 1.0 / k : 0.0;
            double recall = hit ? 1.0 : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
            double ap = hit ? 1.0 / (topK.indexOf(testProductId) + 1) : 0.0;

            totalPrecision += precision;
            totalRecall += recall;
            totalF1 += f1;
            totalAP += ap;
            if (hit) hitCount++;
        }

        int evaluated = userIds.size();
        return new EvaluationResult(
                totalPrecision / evaluated, totalRecall / evaluated, totalF1 / evaluated,
                (double) hitCount / evaluated, totalAP / evaluated,
                userProductScores.size(), evaluated);
    }

    /**
     * LOO evaluation với custom CF/RB weights.
     */
    private EvaluationResult evaluateWithWeights(List<UUID> userIds,
            Map<UUID, Set<UUID>> userPurchases,
            Map<UUID, Map<UUID, Double>> userProductScores,
            Map<UUID, Double> globalPopularity,
            double cfWeight, double rbWeight, int k) {

        double totalPrecision = 0, totalRecall = 0, totalF1 = 0, totalAP = 0;
        int hitCount = 0;

        for (UUID userId : userIds) {
            Set<UUID> allPurchased = userPurchases.get(userId);
            Map<UUID, Double> userScores = userProductScores.get(userId);

            UUID testProductId = userScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (testProductId == null) continue;

            // CF scores
            Map<UUID, Double> cfScores = computeCFOnlyScores(userId, userScores, userProductScores, allPurchased);

            // RB scores
            Map<UUID, Double> rbScores = new HashMap<>();
            double maxFreq = userScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            for (Map.Entry<UUID, Double> entry : userScores.entrySet()) {
                rbScores.put(entry.getKey(), entry.getValue() / maxFreq);
            }
            // Add popularity scores for products user hasn't bought
            for (Map.Entry<UUID, Double> entry : globalPopularity.entrySet()) {
                if (!allPurchased.contains(entry.getKey())) {
                    rbScores.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }

            // Combine
            Map<UUID, Double> combined = new HashMap<>();
            Set<UUID> candidates = new HashSet<>();
            candidates.addAll(cfScores.keySet());
            candidates.addAll(rbScores.keySet());
            for (UUID pid : candidates) {
                double cf = cfScores.getOrDefault(pid, 0.0);
                double rb = rbScores.getOrDefault(pid, 0.0);
                combined.put(pid, cfWeight * cf + rbWeight * rb);
            }

            List<UUID> topK = combined.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .limit(k).map(Map.Entry::getKey).collect(Collectors.toList());

            boolean hit = topK.contains(testProductId);
            double precision = hit ? 1.0 / k : 0.0;
            double recall = hit ? 1.0 : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
            double ap = hit ? 1.0 / (topK.indexOf(testProductId) + 1) : 0.0;

            totalPrecision += precision;
            totalRecall += recall;
            totalF1 += f1;
            totalAP += ap;
            if (hit) hitCount++;
        }

        int evaluated = userIds.size();
        return new EvaluationResult(
                totalPrecision / evaluated, totalRecall / evaluated, totalF1 / evaluated,
                (double) hitCount / evaluated, totalAP / evaluated,
                userProductScores.size(), evaluated);
    }

    /**
     * Tính CF-only scores (cosine similarity × neighbor ratings).
     */
    private Map<UUID, Double> computeCFOnlyScores(UUID userId,
            Map<UUID, Double> userScores,
            Map<UUID, Map<UUID, Double>> allScores,
            Set<UUID> excludeProducts) {

        Map<UUID, Double> scores = new HashMap<>();

        for (Map.Entry<UUID, Map<UUID, Double>> otherEntry : allScores.entrySet()) {
            if (otherEntry.getKey().equals(userId)) continue;
            Map<UUID, Double> otherScores = otherEntry.getValue();

            double similarity = cosineSimilarity(userScores, otherScores);
            if (similarity <= 0) continue;

            for (Map.Entry<UUID, Double> productEntry : otherScores.entrySet()) {
                UUID pid = productEntry.getKey();
                if (excludeProducts.contains(pid)) continue;
                scores.merge(pid, similarity * productEntry.getValue(), Double::sum);
            }
        }
        return scores;
    }

    private double cosineSimilarity(Map<UUID, Double> vec1, Map<UUID, Double> vec2) {
        Set<UUID> commonKeys = new HashSet<>(vec1.keySet());
        commonKeys.retainAll(vec2.keySet());
        if (commonKeys.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        for (UUID key : commonKeys) {
            dotProduct += vec1.get(key) * vec2.get(key);
        }

        double mag1 = Math.sqrt(vec1.values().stream().mapToDouble(v -> v * v).sum());
        double mag2 = Math.sqrt(vec2.values().stream().mapToDouble(v -> v * v).sum());

        if (mag1 == 0.0 || mag2 == 0.0) return 0.0;
        return dotProduct / (mag1 * mag2);
    }
}
