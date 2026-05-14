package com.coffeeshop.config;

import com.coffeeshop.entity.Category;
import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.CategoryRepository;
import com.coffeeshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class CatalogMetadataBackfillRunner implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        backfillCategoryCodes();
        backfillProductCodes();
    }

    private void backfillCategoryCodes() {
        long counter = 0;
        List<Category> updates = new ArrayList<>();
        for (Category category : categoryRepository.findAll()) {
            if (!StringUtils.hasText(category.getCategoryCode())) {
                counter++;
                String code;
                long num = counter;
                do {
                    code = String.format("CAT-%05d", num);
                    num++;
                } while (categoryRepository.findByCategoryCodeIgnoreCase(code).isPresent());
                category.setCategoryCode(code);
                updates.add(category);
            }
        }

        if (!updates.isEmpty()) {
            categoryRepository.saveAll(updates);
        }
    }

    private void backfillProductCodes() {
        long counter = 0;
        List<Product> updates = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            if (!StringUtils.hasText(product.getProductCode())) {
                counter++;
                String code;
                long num = counter;
                do {
                    code = String.format("PRD-%05d", num);
                    num++;
                } while (productRepository.findByProductCodeIgnoreCase(code).isPresent());
                product.setProductCode(code);
                updates.add(product);
            }
        }

        if (!updates.isEmpty()) {
            productRepository.saveAll(updates);
        }
    }
}
