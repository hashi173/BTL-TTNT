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
 *    - Mục đích: Gợi ý sản phẩm dựa trên hành vi của nhiều người dùng có sở thích tương đồng.
 *    - Tính Cosine Similarity giữa các cặp user
 *    - KNN (K=5): chọn K người dùng gần nhất (giống nhất)
 *    - Dự đoán điểm cho sản phẩm chưa mua
 *
 * 2. Rule-based (RB) - Trọng số 0.4:
 *    - Xử lý các ngoại lệ và bổ sung độ chính xác.
 *    - User mới (Cold Start): tự động gợi ý các sản phẩm Best Seller.
 *    - User cũ (Returning User): ưu tiên gợi ý lại món đã từng order nhiều lần/rating cao.
 *
 * 3. Kết hợp: Tính final score = 0.6 × CF + 0.4 × RB
 *
 * 4. Cross-selling: 
 *    - "Bạn có thể cũng thích" (Gợi ý thêm 3 sản phẩm khi người dùng thêm vào giỏ hàng).
 *    - Ưu tiên sản phẩm thường được mua cùng, nếu thiếu sẽ fallback về cùng danh mục.
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
    private static final int MAX_RECOMMENDATIONS = 5;
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
            return productRepository.findAllWithDetails().stream()
                    .filter(Product::isAvailable)
                    .limit(MAX_RECOMMENDATIONS)
                    .collect(Collectors.toList());
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

    /**
     * Gợi ý Cross-selling (Bán chéo)
     * "Bạn có thể cũng thích" - Gợi ý thêm khoảng 3 món khi thêm một món vào giỏ hàng.
     * Sử dụng dữ liệu những người từng mua sản phẩm này cũng mua sản phẩm nào khác.
     */
    public List<Product> getCrossSellingRecommendations(UUID productId) {
        ensureMatricesBuilt();
        
        // Find users who bought this product
        List<UUID> usersWhoBought = new ArrayList<>();
        if (userProductMatrix != null) {
            for (Map.Entry<UUID, Map<UUID, Double>> entry : userProductMatrix.entrySet()) {
                if (entry.getValue().containsKey(productId)) {
                    usersWhoBought.add(entry.getKey());
                }
            }
        }
        
        // If not enough data, fallback to category-based or best sellers
        if (usersWhoBought.size() < 2) {
            return getFallbackCrossSelling(productId);
        }
        
        // Aggregate what else they bought
        Map<UUID, Double> coOccurrenceScores = new HashMap<>();
        for (UUID uid : usersWhoBought) {
            Map<UUID, Double> ratings = userProductMatrix.get(uid);
            for (Map.Entry<UUID, Double> rating : ratings.entrySet()) {
                UUID otherProductId = rating.getKey();
                if (!otherProductId.equals(productId)) {
                    coOccurrenceScores.merge(otherProductId, rating.getValue(), Double::sum);
                }
            }
        }
        
        if (coOccurrenceScores.isEmpty()) {
             return getFallbackCrossSelling(productId);
        }
        
        // Sort by score descending
        List<UUID> recommendedIds = coOccurrenceScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
                
        List<Product> allProducts = productRepository.findAllWithDetails();
        Map<UUID, Product> productMap = allProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
                
        List<Product> results = recommendedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .filter(Product::isAvailable)
                .collect(Collectors.toList());

        // Fill up to 3 if we didn't find enough
        if (results.size() < 3) {
             List<Product> fallbacks = getFallbackCrossSelling(productId);
             for (Product p : fallbacks) {
                 if (!results.contains(p) && results.size() < 3) {
                     results.add(p);
                 }
             }
        }

        return results;
    }

    private List<Product> getFallbackCrossSelling(UUID productId) {
        Product currentProduct = productRepository.findById(productId).orElse(null);
        if (currentProduct == null || currentProduct.getCategory() == null) {
            return getBestSellers().stream().limit(3).collect(Collectors.toList());
        }
        
        UUID categoryId = currentProduct.getCategory().getId();
        
        List<Product> categoryProducts = productRepository.findAllWithDetails().stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                .filter(p -> !p.getId().equals(productId))
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
                
        Collections.shuffle(categoryProducts);
        return categoryProducts.stream().limit(3).collect(Collectors.toList());
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
