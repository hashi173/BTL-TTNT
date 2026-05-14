package com.coffeeshop.service.ai;

import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Baseline recommenders để so sánh với hệ thống hybrid.
 *
 * ═══════════════════════════════════════════════════════════════
 * MỤC ĐÍCH
 * ═══════════════════════════════════════════════════════════════
 * Không có baseline, không thể biết hệ thống hybrid tốt hơn "đoán mò"
 * bao nhiêu. Hai baseline:
 *
 * 1. Random: gợiý ngẫu nhiên → đo FLOOR (tệ nhất có thể)
 * 2. Popularity: gợiý sản phẩm bán chạy → đo CEILING đơn giản
 *
 * Nếu hybrid > popularity > random → hệ thống có giá trị.
 * Nếu hybrid ≈ popularity → CF không đóng góp gì.
 */
@Service
@RequiredArgsConstructor
public class BaselineRecommender {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * Baseline 1: Random Recommendation.
     * Gợiý K sản phẩm ngẫu nhiên từ catalog.
     */
    public List<Product> recommendRandom(int k, long seed) {
        List<Product> all = productRepository.findAllWithDetails().stream()
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
        if (all.isEmpty()) return List.of();

        Collections.shuffle(all, new Random(seed));
        return all.stream().limit(k).collect(Collectors.toList());
    }

    /**
     * Baseline 2: Popularity-based Recommendation.
     * Gợiý K sản phẩm bán chạy nhất (theo tổng số lượng đã bán).
     */
    public List<Product> recommendByPopularity(int k) {
        List<Object[]> totals = orderItemRepository.findProductPurchaseTotals();
        if (totals.isEmpty()) {
            return productRepository.findAllWithDetails().stream()
                    .filter(Product::isAvailable)
                    .limit(k)
                    .collect(Collectors.toList());
        }

        Map<UUID, Product> productMap = productRepository.findAllWithDetails().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return totals.stream()
                .limit(k)
                .map(row -> productMap.get((UUID) row[0]))
                .filter(Objects::nonNull)
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Đánh giá random baseline bằng LOO trên ma trận cho trước.
     * Dùng seed cố định để kết quả reproducible.
     */
    public double evaluateRandomLOO(Map<UUID, Map<UUID, Double>> userProductMatrix,
            Set<UUID> productIds, int k) {
        int hits = 0;
        int evaluated = 0;
        Random rand = new Random(42);
        List<UUID> productList = new ArrayList<>(productIds);

        for (Map.Entry<UUID, Map<UUID, Double>> entry : userProductMatrix.entrySet()) {
            Map<UUID, Double> purchases = entry.getValue();
            if (purchases.size() < 2) continue;

            // Leave-one-out: chọn sản phẩm mua nhiều nhất làm test
            UUID testProduct = purchases.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (testProduct == null) continue;

            // Random K products (loại trừ sản phẩm đã mua)
            List<UUID> candidates = productList.stream()
                    .filter(pid -> !purchases.containsKey(pid))
                    .collect(Collectors.toList());
            Collections.shuffle(candidates, rand);
            Set<UUID> recommended = new HashSet<>(candidates.stream().limit(k).collect(Collectors.toSet()));

            if (recommended.contains(testProduct)) hits++;
            evaluated++;
        }

        return evaluated > 0 ? (double) hits / evaluated : 0.0;
    }

    /**
     * Đánh giá popularity baseline bằng LOO trên ma trận cho trước.
     */
    public double evaluatePopularityLOO(Map<UUID, Map<UUID, Double>> userProductMatrix,
            Map<UUID, Double> globalPopularity, int k) {
        // Top-K phổ biến nhất (cố định cho mọi user)
        List<UUID> topK = globalPopularity.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        int hits = 0;
        int evaluated = 0;

        for (Map.Entry<UUID, Map<UUID, Double>> entry : userProductMatrix.entrySet()) {
            Map<UUID, Double> purchases = entry.getValue();
            if (purchases.size() < 2) continue;

            UUID testProduct = purchases.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (testProduct == null) continue;

            if (topK.contains(testProduct)) hits++;
            evaluated++;
        }

        return evaluated > 0 ? (double) hits / evaluated : 0.0;
    }
}
