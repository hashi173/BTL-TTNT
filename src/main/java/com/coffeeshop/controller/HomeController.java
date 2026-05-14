package com.coffeeshop.controller;

import com.coffeeshop.entity.Product;
import com.coffeeshop.service.CategoryService;
import com.coffeeshop.service.ProductService;
import com.coffeeshop.service.RecommendationService;
import com.coffeeshop.service.ToppingService;
import com.coffeeshop.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ToppingService toppingService;
    private final RecommendationService recommendationService;
    private final UserService userService;

    @GetMapping("/")
    public String home(Model model, jakarta.servlet.http.HttpServletRequest request) {
        request.getSession(true); // ensure session exists for CSRF token
        model.addAttribute("products", productService.getAllProducts());
        model.addAttribute("categories", categoryService.getAllCategories());

        // Recommendations
        java.security.Principal principal = request.getUserPrincipal();
        if (principal != null) {
            userService.findByUsername(principal.getName()).ifPresent(user -> {
                List<Product> recommendations = recommendationService.getRecommendations(user.getId());
                model.addAttribute("recommendations", recommendations);
            });
        } else {
            model.addAttribute("recommendations", recommendationService.getBestSellers());
        }

        return "home";
    }

    /** Partial fragment used by AJAX category/search filtering on the home menu. */
    @GetMapping("/products/fragment")
    public String getProductsFragment(
            @org.springframework.web.bind.annotation.RequestParam(name = "categoryId", required = false) java.util.UUID categoryId,
            @org.springframework.web.bind.annotation.RequestParam(name = "keyword", required = false) String keyword,
            Model model) {
        model.addAttribute("products", productService.searchProductsForMenu(keyword, categoryId));
        return "home :: productList";
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable("id") java.util.UUID id, Model model) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + id));
        model.addAttribute("product", product);
        model.addAttribute("toppings", toppingService.getAllToppings());
        return "product/detail";
    }
}
