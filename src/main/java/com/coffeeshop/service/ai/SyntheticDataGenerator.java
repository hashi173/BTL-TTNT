package com.coffeeshop.service.ai;

import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Trình tạo dữ liệu tổng hợp (synthetic data) cho đánh giá hệ thống gợiý.
 *
 * ═══════════════════════════════════════════════════════════════
 * MỤC ĐÍCH
 * ═══════════════════════════════════════════════════════════════
 * Với chỉ 30 user thực trong database, ma trận user-product quá thưa
 * để đánh giá CF có ý nghĩa thống kê. Service này tạo N user giả lập
 * (100, 200, 500...) với preference clusters rõ ràng, cho phép:
 * - Test CF với dataset lớn hơn
 * - Đánh giá scalability
 * - Tạo dữ liệu cho ablation study
 *
 * ═══════════════════════════════════════════════════════════════
 * THUẬT TOÁN
 * ═══════════════════════════════════════════════════════════════
 * 1. Chia sản phẩm thành 4 clusters: Coffee, Tea, Smoothie, Juice
 * 2. Tạo N synthetic users, mỗi user thuộc 1 cluster
 * 3. Với mỗi user, tạo M purchase events:
 *    - 70% từ cluster ưa thích
 *    - 30% ngẫu nhiên từ cluster khác
 * 4. Trả về ma trận user-product (không lưu DB)
 */
@Service
@RequiredArgsConstructor
public class SyntheticDataGenerator {

    private final ProductRepository productRepository;

    /**
     * Sinh ma trận user-product tổng hợp.
     *
     * @param numUsers   Số user cần tạo (ví dụ: 100, 200, 500)
     * @param minPurchases Số purchase tối thiểu mỗi user
     * @param maxPurchases Số purchase tối đa mỗi user
     * @return Map<userId, Map<productId, score>>
     */
    public Map<UUID, Map<UUID, Double>> generate(int numUsers, int minPurchases, int maxPurchases) {
        List<Product> allProducts = productRepository.findAllWithDetails();
        if (allProducts.isEmpty()) return Collections.emptyMap();

        // Chia sản phẩm theo category
        Map<String, List<Product>> byCategory = new HashMap<>();
        for (Product p : allProducts) {
            String cat = p.getCategory() != null ? p.getCategory().getName() : "Other";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(p);
        }

        List<String> categoryNames = new ArrayList<>(byCategory.keySet());
        if (categoryNames.isEmpty()) return Collections.emptyMap();

        Random rand = new Random(42);
        Map<UUID, Map<UUID, Double>> matrix = new HashMap<>();

        for (int i = 0; i < numUsers; i++) {
            UUID userId = UUID.randomUUID();
            // Gán cluster cho user này
            String preferredCategory = categoryNames.get(i % categoryNames.size());
            List<Product> preferred = byCategory.get(preferredCategory);
            List<Product> others = allProducts.stream()
                    .filter(p -> !preferred.contains(p))
                    .toList();

            int purchases = minPurchases + rand.nextInt(maxPurchases - minPurchases + 1);
            Map<UUID, Double> userScores = new HashMap<>();

            for (int j = 0; j < purchases; j++) {
                Product product;
                if (rand.nextDouble() < 0.7 && !preferred.isEmpty()) {
                    product = preferred.get(rand.nextInt(preferred.size()));
                } else if (!others.isEmpty()) {
                    product = others.get(rand.nextInt(others.size()));
                } else {
                    product = preferred.get(rand.nextInt(preferred.size()));
                }
                userScores.merge(product.getId(), 1.0, Double::sum);
            }

            // Normalize
            double max = userScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            if (max > 0) {
                userScores.replaceAll((k, v) -> v / max);
            }

            matrix.put(userId, userScores);
        }

        return matrix;
    }

    /**
     * Sinh ma trận với cấu hình mặc định: 100 users, 10-30 purchases mỗi user.
     */
    public Map<UUID, Map<UUID, Double>> generateDefault() {
        return generate(100, 10, 30);
    }
}
