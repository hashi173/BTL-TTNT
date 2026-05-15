package com.coffeeshop.controller;

import com.coffeeshop.dto.CartItem;
import com.coffeeshop.entity.Product;
import com.coffeeshop.entity.ProductSize;
import com.coffeeshop.entity.Topping;
import com.coffeeshop.service.CartService;
import com.coffeeshop.service.ProductService;
import com.coffeeshop.service.ToppingService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import com.coffeeshop.service.RecommendationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final ToppingService toppingService;
    private final RecommendationService recommendationService;

    @GetMapping
    public String viewCart(HttpSession session, Model model) {
        com.coffeeshop.dto.Cart cart = cartService.getCart(session);
        model.addAttribute("cart", cart);
        
        if (cart != null && cart.getItems() != null && !cart.getItems().isEmpty()) {
            CartItem lastItem = cart.getItems().get(cart.getItems().size() - 1);
            List<Product> crossSelling = recommendationService.getCrossSellingRecommendations(lastItem.getProductId());
            model.addAttribute("crossSellingProducts", crossSelling);
        }
        
        return "cart/index";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam("productId") java.util.UUID productId,
            @RequestParam(value = "sizeId", required = false) java.util.UUID sizeId,
            @RequestParam("quantity") Integer quantity,
            @RequestParam(value = "toppingIds", required = false) List<java.util.UUID> toppingIds,
            @RequestParam(value = "sugar", defaultValue = "100%") String sugar,
            @RequestParam(value = "ice", defaultValue = "100%") String ice,
            @RequestParam(value = "note", required = false, defaultValue = "") String note,
            HttpSession session) {

        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product"));

        // Use provided sizeId, or default to the first available size if not provided
        java.util.UUID finalSizeId = sizeId;
        ProductSize selectedSize = product.getSizes().stream()
                .filter(s -> finalSizeId == null || s.getId().equals(finalSizeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sizes defined for this product"));

        List<Topping> toppings = new ArrayList<>();
        List<String> toppingNames = new ArrayList<>();
        double toppingsPrice = 0.0;

        if (toppingIds != null && !toppingIds.isEmpty()) {
            List<Topping> allToppings = toppingService.getAllToppings();
            for (java.util.UUID tId : toppingIds) {
                allToppings.stream().filter(t -> t.getId().equals(tId)).findFirst().ifPresent(t -> {
                    toppings.add(t);
                    toppingNames.add(t.getName());
                });
            }
            toppingsPrice = toppings.stream().mapToDouble(Topping::getPrice).sum();
        }

        List<java.util.UUID> normalizedToppingIds = new ArrayList<>(toppingIds != null ? toppingIds : List.of());
        normalizedToppingIds.sort(java.util.Comparator.comparing(java.util.UUID::toString));

        String normalizedNote = note != null ? note.trim() : "";
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("sugar", sugar);
        attributes.put("ice", ice);
        if (!normalizedNote.isEmpty()) {
            attributes.put("note", normalizedNote);
        }

        double unitPrice = selectedSize.getPrice() + toppingsPrice;

        CartItem item = new CartItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());

        item.setProductImage(product.getResolvedImagePath());

        item.setSizeId(selectedSize.getId());
        item.setSizeName(selectedSize.getSizeName());
        item.setPrice(unitPrice);
        item.setQuantity(quantity);

        // Sugar/ice labels appended as topping display entries when non-default
        if (!"100%".equals(sugar)) toppingNames.add("Sugar " + sugar);
        if (!"100%".equals(ice)) toppingNames.add("Ice " + ice);

        item.setToppingIds(normalizedToppingIds);
        item.setToppingNames(toppingNames);
        item.setAttributes(attributes);
        item.setNote(normalizedNote);

        cartService.addItemToCart(session, item);
        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String updateQuantity(@RequestParam("index") int index, @RequestParam("quantity") int quantity,
            HttpSession session) {
        cartService.updateCartItem(session, index, quantity);
        return "redirect:/cart";
    }

    @GetMapping("/remove/{index}")
    public String removeItem(@PathVariable("index") int index, HttpSession session) {
        cartService.removeCartItem(session, index);
        return "redirect:/cart";
    }
}
