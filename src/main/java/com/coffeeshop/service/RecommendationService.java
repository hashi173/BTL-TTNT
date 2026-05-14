package com.coffeeshop.service;

import com.coffeeshop.entity.OrderItem;
import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hệ thống gợi ý sản phẩm kết hợp Collaborative Filtering và Rule-based.
 *
 * ═══════════════════════════════════════════════════════════════
 * THUẬT TOÁN: Hybrid Recommendation System
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Collaborative Filtering (CF) - Trọng số 0.6:
 *    - Xây dựng ma trận User-Product từ đơn hàng đã hoàn thành
 *    - Tính Cosine Similarity giữa các cặp user
 *    - KNN (K=5): tìm 5 user tương tự nhất
 *    - Dự đoán điểm cho sản phẩm chưa mua
 *
 * 2. Rule-based (RB) - Trọng số 0.4:
 *    - Cold Start: user mới → sản phẩm bán chạy
 *    - Returning User: tăng điểm sản phẩm mua thường xuyên
 *
 * 3. Kết hợp: finalScore = 0.6 × CF + 0.4 × RB
 *
 * ═══════════════════════════════════════════════════════════════
 * CACHING
 * ═══════════════════════════════════════════════════════════════
 * - Ma trận cache trong bộ nhớ, TTL 30 phút
 * - Kết quả cache bằng Spring @Cacheable
 * - Auto-invalidate khi đặt hàng
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    private static final double CF_WEIGHT = 0.6;
    private static final double RB_WEIGHT = 0.4;
    private static final int K_NEIGHBORS = 5;
    private static final int MAX_RECOMMENDATIONS = 6;
    private static final long MATRIX_TTL_MS = 30 * 60 * 1000; // 30 minutes

    // In-memory matrix cache
    private volatile Map<UUID, Map<UUID, Double>> userProductMatrix;
    private volatile Map<UUID, Map<UUID, Double>> userSimilarityMatrix;
    private volatile Set<UUID> allProductIds;
    private volatile long matrixLastBuilt = 0;

    /**
     * Gợi ý sản phẩm cho user kết hợp CF + Rule-based.
     *
     * Quy trình:
     * 1. Đảm bảo ma trận đã được xây dựng
     * 2. Kiểm tra user mới → trả về best sellers
     * 3. Tính CF score (cosine similarity × KNN)
     * 4. Tính Rule-based score (best sellers / frequency)
     * 5. Kết hợp: 0.6 × CF + 0.4 × RB
     * 6. Trả về top 6 sản phẩm
     */
    @Cacheable(value = "recommendations", key = "#userId")
    public List<Product> getRecommendations(UUID userId) {
        ensureMatricesBuilt();

        if (userProductMatrix == null || userProductMatrix.isEmpty()) {
            return getBestSellers();
        }

        Map<UUID, Double> userRatings = userProductMatrix.get(userId);
        boolean isNewUser = (userRatings == null || userRatings.isEmpty());

        if (isNewUser) {
            return getBestSellers();
        }

        // Compute scores from both systems
        Map<UUID, Double> cfScores = computeCFScores(userId);
        Map<UUID, Double> rbScores = computeRuleBasedScores(userId);

        // Combine scores
        Map<UUID, Double> combinedScores = new HashMap<>();
        Set<UUID> candidateProducts = new HashSet<>();
        candidateProducts.addAll(cfScores.keySet());
        candidateProducts.addAll(rbScores.keySet());

        // Exclude products the user has already purchased heavily
        // (we still recommend them if score is high enough, but with reduced priority)
        for (UUID productId : candidateProducts) {
            double cf = cfScores.getOrDefault(productId, 0.0);
            double rb = rbScores.getOrDefault(productId, 0.0);
            combinedScores.put(productId, CF_WEIGHT * cf + RB_WEIGHT * rb);
        }

        // Sort by combined score descending, take top N
        List<UUID> recommendedIds = combinedScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(MAX_RECOMMENDATIONS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (recommendedIds.isEmpty()) {
            return getBestSellers();
        }

        // Fetch full product entities
        List<Product> allProducts = productRepository.findAllWithDetails();
        Map<UUID, Product> productMap = allProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return recommendedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
    }

    public List<Product> getBestSellers() {
        List<Object[]> totals = orderItemRepository.findProductPurchaseTotals();
        if (totals.isEmpty()) {
            return productRepository.findAllWithDetails();
        }

        List<UUID> topProductIds = totals.stream()
                .limit(MAX_RECOMMENDATIONS)
                .map(row -> (UUID) row[0])
                .collect(Collectors.toList());

        List<Product> allProducts = productRepository.findAllWithDetails();
        Map<UUID, Product> productMap = allProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Product> result = topProductIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .filter(Product::isAvailable)
                .collect(Collectors.toList());

        // If not enough best sellers, fill with available products
        if (result.size() < MAX_RECOMMENDATIONS) {
            Set<UUID> existingIds = result.stream().map(Product::getId).collect(Collectors.toSet());
            allProducts.stream()
                    .filter(Product::isAvailable)
                    .filter(p -> !existingIds.contains(p.getId()))
                    .limit(MAX_RECOMMENDATIONS - result.size())
                    .forEach(result::add);
        }

        return result;
    }

    @CacheEvict(value = "recommendations", key = "#userId")
    public void evictCacheForUser(UUID userId) {
        // Matrix will be rebuilt on next request
        matrixLastBuilt = 0;
    }

    @CacheEvict(value = "recommendations", allEntries = true)
    public void invalidateAllRecommendations() {
        matrixLastBuilt = 0;
    }

    /**
     * Xây dựng ma trận user-product và ma trận similarity.
     *
     * Bước 1: Lấy tất cả order items đã hoàn thành
     * Bước 2: Xây dựng ma trận user × product (quantity)
     * Bước 3: Normalize mỗi user vector (chia cho max → 0-1)
     * Bước 4: Tính Cosine Similarity cho tất cả cặp user
     *
     * Cosine Similarity: cos(θ) = dot(A,B) / (|A| × |B|)
     * Chỉ tính trên key chung (intersection) để tối ưu
     */
    private synchronized void ensureMatricesBuilt() {
        if (System.currentTimeMillis() - matrixLastBuilt < MATRIX_TTL_MS
                && userProductMatrix != null) {
            return;
        }

        log.info("Building recommendation matrices...");
        List<OrderItem> allItems = orderItemRepository.findAllCompletedOrderItems();

        if (allItems.isEmpty()) {
            userProductMatrix = Collections.emptyMap();
            userSimilarityMatrix = Collections.emptyMap();
            allProductIds = Collections.emptySet();
            matrixLastBuilt = System.currentTimeMillis();
            return;
        }

        // Step 1: Build user-product interaction matrix
        Map<UUID, Map<UUID, Double>> matrix = new HashMap<>();
        Set<UUID> productIds = new HashSet<>();

        for (OrderItem item : allItems) {
            if (item.getOrder() == null || item.getOrder().getUser() == null
                    || item.getProduct() == null) {
                continue;
            }
            UUID userId = item.getOrder().getUser().getId();
            UUID productId = item.getProduct().getId();
            double quantity = item.getQuantity() != null ? item.getQuantity() : 1;

            productIds.add(productId);
            matrix.computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(productId, quantity, Double::sum);
        }

        // Normalize each user's vector by their max quantity (0-1 scale)
        for (Map<UUID, Double> userVec : matrix.values()) {
            double maxVal = userVec.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            if (maxVal > 0) {
                userVec.replaceAll((k, v) -> v / maxVal);
            }
        }

        // Step 2: Compute cosine similarity between all user pairs
        Map<UUID, Map<UUID, Double>> similarityMatrix = new HashMap<>();
        List<UUID> userIds = new ArrayList<>(matrix.keySet());

        for (int i = 0; i < userIds.size(); i++) {
            UUID u1 = userIds.get(i);
            Map<UUID, Double> vec1 = matrix.get(u1);
            similarityMatrix.put(u1, new HashMap<>());

            for (int j = i + 1; j < userIds.size(); j++) {
                UUID u2 = userIds.get(j);
                Map<UUID, Double> vec2 = matrix.get(u2);

                double sim = cosineSimilarity(vec1, vec2);
                similarityMatrix.get(u1).put(u2, sim);
                similarityMatrix.computeIfAbsent(u2, k -> new HashMap<>()).put(u1, sim);
            }
        }

        userProductMatrix = matrix;
        userSimilarityMatrix = similarityMatrix;
        allProductIds = productIds;
        matrixLastBuilt = System.currentTimeMillis();
        log.info("Recommendation matrices built: {} users, {} products", matrix.size(), productIds.size());
    }

    /**
     * Tính Cosine Similarity giữa 2 vector thưa.
     *
     * cos(θ) = (A · B) / (|A| × |B|)
     * Chỉ tính trên key chung (intersection) để tối ưu cho sparse vectors.
     *
     * @return Giá trị từ 0.0 (hoàn toàn khác) đến 1.0 (hoàn toàn giống)
     */
    private double cosineSimilarity(Map<UUID, Double> vec1, Map<UUID, Double> vec2) {
        // Find common keys for dot product
        Set<UUID> commonKeys = new HashSet<>(vec1.keySet());
        commonKeys.retainAll(vec2.keySet());

        if (commonKeys.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (UUID key : commonKeys) {
            dotProduct += vec1.get(key) * vec2.get(key);
        }

        double mag1 = Math.sqrt(vec1.values().stream().mapToDouble(v -> v * v).sum());
        double mag2 = Math.sqrt(vec2.values().stream().mapToDouble(v -> v * v).sum());

        if (mag1 == 0.0 || mag2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (mag1 * mag2);
    }

    /**
     * Tính điểm Collaborative Filtering cho user.
     *
     * Thuật toán KNN (K=5):
     * 1. Tìm top-K user tương tự nhất (theo cosine similarity)
     * 2. Với mỗi sản phẩm user chưa mua:
     *    score = Σ(similarity × neighbor_rating) / Σ|similarity|
     *
     * Trọng số: CF_WEIGHT = 0.6
     */
    private Map<UUID, Double> computeCFScores(UUID userId) {
        Map<UUID, Double> scores = new HashMap<>();

        if (userSimilarityMatrix == null || !userSimilarityMatrix.containsKey(userId)) {
            return scores;
        }

        Map<UUID, Double> similarities = userSimilarityMatrix.get(userId);
        Map<UUID, Double> userRatings = userProductMatrix.getOrDefault(userId, Collections.emptyMap());

        // Find top-K neighbors
        List<Map.Entry<UUID, Double>> sortedNeighbors = similarities.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(K_NEIGHBORS)
                .collect(Collectors.toList());

        // For each product the user hasn't purchased, compute weighted score
        for (UUID productId : allProductIds) {
            if (userRatings.containsKey(productId)) {
                continue; // Skip products user already purchased
            }

            double weightedSum = 0.0;
            double similaritySum = 0.0;

            for (Map.Entry<UUID, Double> neighbor : sortedNeighbors) {
                UUID neighborId = neighbor.getKey();
                double similarity = neighbor.getValue();

                Map<UUID, Double> neighborRatings = userProductMatrix.get(neighborId);
                if (neighborRatings != null && neighborRatings.containsKey(productId)) {
                    weightedSum += similarity * neighborRatings.get(productId);
                    similaritySum += Math.abs(similarity);
                }
            }

            if (similaritySum > 0) {
                scores.put(productId, weightedSum / similaritySum);
            }
        }

        return scores;
    }

    /**
     * Tính điểm Rule-based cho user.
     *
     * Quy tắc 1 - Cold Start (user mới):
     *   score = purchaseCount / maxPurchaseCount
     *   → Ưu tiên sản phẩm bán chạy
     *
     * Quy tắc 2 - Returning User:
     *   score = userFrequency / maxUserFrequency
     *   → Ưu tiên sản phẩm user mua thường xuyên
     *
     * Trọng số: RB_WEIGHT = 0.4
     */
    private Map<UUID, Double> computeRuleBasedScores(UUID userId) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Double> userRatings = userProductMatrix.getOrDefault(userId, Collections.emptyMap());

        if (userRatings.isEmpty()) {
            // Rule 1: New user - best sellers
            List<Object[]> totals = orderItemRepository.findProductPurchaseTotals();
            if (!totals.isEmpty()) {
                double maxCount = totals.isEmpty() ? 1.0 : ((Number) totals.get(0)[1]).doubleValue();
                for (Object[] row : totals) {
                    UUID productId = (UUID) row[0];
                    double count = ((Number) row[1]).doubleValue();
                    scores.put(productId, count / maxCount);
                }
            }
        } else {
            // Rule 2: Returning user - boost frequently purchased items
            double maxFreq = userRatings.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            for (Map.Entry<UUID, Double> entry : userRatings.entrySet()) {
                scores.put(entry.getKey(), entry.getValue() / maxFreq);
            }
        }

        return scores;
    }
}
