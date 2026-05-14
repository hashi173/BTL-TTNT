package com.coffeeshop;

import com.coffeeshop.entity.Category;
import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.CategoryRepository;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.ProductRepository;
import com.coffeeshop.service.CategoryService;
import com.coffeeshop.service.ProductService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogCodeGenerationTest {

    @Test
    void saveCategoryGeneratesReadableCode() {
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        CategoryService categoryService = new CategoryService(categoryRepository);

        Category category = new Category();
        category.setName("Cà phê truyền thống");

        when(categoryRepository.findByCategoryCodeIgnoreCase("CAT-00001")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category saved = categoryService.saveCategory(category);

        assertEquals("CAT-00001", saved.getCategoryCode());
    }

    @Test
    void saveProductGeneratesReadableCodeAndKeepsSizesLinked() {
        ProductRepository productRepository = mock(ProductRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ProductService productService = new ProductService(productRepository, orderItemRepository);

        Product product = new Product();
        product.setName("Trà đào cam sả");
        product.setSizes(List.of());

        when(productRepository.findByProductCodeIgnoreCase("PRD-00001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = productService.saveProduct(product);

        assertEquals("PRD-00001", saved.getProductCode());
    }
}
