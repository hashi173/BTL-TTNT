package com.coffeeshop.service;

import com.coffeeshop.dto.Cart;
import com.coffeeshop.dto.CartItem;
import com.coffeeshop.entity.Order;
import com.coffeeshop.entity.OrderItem;
import com.coffeeshop.entity.OrderStatus;
import com.coffeeshop.entity.Product;
import com.coffeeshop.entity.User;
import com.coffeeshop.repository.OrderItemRepository;
import com.coffeeshop.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final com.coffeeshop.repository.ProductRepository productRepository;
    private final RecommendationService recommendationService;

    @Transactional
    public Order placeOrder(Cart cart, User user, String customerName, String phone, String address, String note) {
        Order order = new Order();
        order.setUser(user);
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setAddress(address);
        order.setNote(note);
        order.setTotalAmount(cart.getTotalAmount());
        order.setSubTotal(BigDecimal.valueOf(cart.getTotalAmount()));
        order.setGrandTotal(BigDecimal.valueOf(cart.getTotalAmount()));
        order.setStatus(OrderStatus.PENDING);
        order.setOrderStatus(OrderStatus.PENDING.name());
        order.setTrackingCode(generateTrackingCode());

        Order savedOrder = orderRepository.save(order);

        for (CartItem item : cart.getItems()) {
            OrderItem detail = new OrderItem();
            detail.setOrder(savedOrder);
            detail.setSnapshotProductName(item.getProductName());
            detail.setQuantity(item.getQuantity());
            detail.setSnapshotUnitPrice(BigDecimal.valueOf(item.getPrice()));
            detail.setSubTotal(BigDecimal.valueOf(item.getPrice() * item.getQuantity()));
            detail.setSnapshotOptions(buildSnapshotOptions(item));

            if (item.getProductId() != null) {
                try {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null) {
                        detail.setProduct(product);
                    }
                } catch (Exception ignored) {
                    // Snapshot fields are enough if the original product is unavailable.
                }
            }

            orderItemRepository.save(detail);
        }

        // Evict recommendation cache for this user
        if (user != null) {
            recommendationService.evictCacheForUser(user.getId());
        }

        return savedOrder;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(java.util.UUID id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Transactional
    public void updateOrderStatus(java.util.UUID orderId, OrderStatus status) {
        Order order = getOrderById(orderId);
        if (order != null) {
            OrderStatus current = order.getStatus();
            // Don't allow changes to finalized orders
            if (current == OrderStatus.COMPLETED || current == OrderStatus.CANCELLED) {
                return;
            }
            // Allow CANCEL from any active status
            if (status == OrderStatus.CANCELLED) {
                order.setStatus(status);
                order.setOrderStatus(status.name());
                orderRepository.save(order);
                return;
            }
            // Only allow forward progression (higher ordinal)
            if (status.ordinal() < current.ordinal()) {
                return;
            }
            order.setStatus(status);
            order.setOrderStatus(status.name());
            orderRepository.save(order);
        }
    }

    /** Returns the total number of orders across all statuses. */
    public long countTotalOrders() {
        return orderRepository.count();
    }

    public long countPendingOrders() {
        return orderRepository.countByStatus(OrderStatus.PENDING);
    }

    public List<Order> getPendingOrders() {
        return orderRepository.findAllByStatusOrderByCreatedAtDesc(OrderStatus.PENDING);
    }

    public List<Order> getActiveOrders() {
        return orderRepository.findAllByStatusInOrderByCreatedAtDesc(
                java.util.Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING));
    }

    public double calculateTotalRevenue() {
        Double revenue = orderRepository.sumTotalAmountByStatus(OrderStatus.COMPLETED);
        return revenue != null ? revenue : 0.0;
    }

    private String generateTrackingCode() {
        long nextNumber = orderRepository.count() + 1;
        String trackingCode;
        do {
            trackingCode = String.format("ORD-%06d", nextNumber);
            nextNumber++;
        } while (orderRepository.findByTrackingCode(trackingCode).isPresent());
        return trackingCode;
    }

    private String buildSnapshotOptions(CartItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<String> summaryParts = new ArrayList<>();

        if (item.getSizeName() != null && !item.getSizeName().isBlank()) {
            String sizeName = item.getSizeName().trim();
            snapshot.put("size", sizeName);
            summaryParts.add("Size: " + sizeName);
        }

        if (item.getToppingNames() != null && !item.getToppingNames().isEmpty()) {
            snapshot.put("options", item.getToppingNames());
            summaryParts.add("Options: " + String.join(", ", item.getToppingNames()));
        }

        if (item.getAttributes() != null) {
            String sugar = item.getAttributes().get("sugar");
            if (sugar != null && !sugar.isBlank()) {
                snapshot.put("sugar", sugar.trim());
                summaryParts.add("Sugar: " + sugar.trim());
            }

            String ice = item.getAttributes().get("ice");
            if (ice != null && !ice.isBlank()) {
                snapshot.put("ice", ice.trim());
                summaryParts.add("Ice: " + ice.trim());
            }
        }

        if (item.getNote() != null && !item.getNote().isBlank()) {
            String note = item.getNote().trim();
            snapshot.put("note", note);
            summaryParts.add("Note: " + note);
        }

        snapshot.put("summary", String.join(" | ", summaryParts));

        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{\"summary\":\"" + escapeJson(String.join(" | ", summaryParts)) + "\"}";
        }
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }



    public List<Object[]> getMonthlyRevenue() {
        return orderRepository.findMonthlyRevenue();
    }

    public List<Object[]> getTopSellingProducts() {
        return orderRepository.findTopSellingProducts(org.springframework.data.domain.PageRequest.of(0, 5));
    }

    public List<Object[]> getTopSellingProductsCurrentMonth() {
        java.time.LocalDate now = java.time.LocalDate.now();
        return orderRepository.findTopSellingProductByMonth(now.getMonthValue(), now.getYear(),
                org.springframework.data.domain.PageRequest.of(0, 5));
    }



    public org.springframework.data.domain.Page<Order> getAllOrdersPaginated(
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public org.springframework.data.domain.Page<Order> getOrdersByStatusPaginated(OrderStatus status,
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    public org.springframework.data.domain.Page<Order> getOrdersByStatusesPaginated(List<OrderStatus> statuses,
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByStatusIn(statuses, pageable);
    }

    public org.springframework.data.domain.Page<Order> searchOrdersPaginated(String keyword,
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.searchOrdersPaginated(keyword, pageable);
    }

    public org.springframework.data.domain.Page<Order> searchOrdersAndStatusPaginated(String keyword,
            OrderStatus status,
            org.springframework.data.domain.Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            if (status != null) {
                return orderRepository.searchOrdersAndStatusPaginated(keyword, status, pageable);
            }
            return orderRepository.searchOrdersPaginated(keyword, pageable);
        }

        if (status != null) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    /** Alias of {@link #countTotalOrders()} for view compatibility. */
    public long getTotalOrders() {
        return countTotalOrders();
    }

    public org.springframework.data.domain.Page<Order> getOrdersByUserPaginated(java.util.UUID userId,
            org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }
}
