package com.coffeeshop;

import com.coffeeshop.controller.HomeController;
import com.coffeeshop.service.CategoryService;
import com.coffeeshop.service.ProductService;
import com.coffeeshop.service.RecommendationService;
import com.coffeeshop.service.ToppingService;
import com.coffeeshop.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ToppingService toppingService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private UserService userService;

    @Mock
    private jakarta.servlet.http.HttpServletRequest request;

    @Mock
    private HttpSession session;

    @InjectMocks
    private HomeController homeController;

    @Test
    void homeUsesBestSellersForAnonymousUser() {
        when(request.getSession(true)).thenReturn(session);
        when(productService.getAllProducts()).thenReturn(List.of());
        when(categoryService.getAllCategories()).thenReturn(List.of());
        when(recommendationService.getBestSellers()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        String viewName = homeController.home(model, request);

        assertEquals("home", viewName);
        assertEquals(List.of(), model.get("products"));
        assertEquals(List.of(), model.get("categories"));
        assertEquals(List.of(), model.get("recommendations"));
        verify(recommendationService).getBestSellers();
    }
}
