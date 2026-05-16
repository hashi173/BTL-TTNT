package com.coffeeshop.repository;

import com.coffeeshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrder_Id(UUID orderId);
    boolean existsByProduct_Id(UUID productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH o.user u JOIN FETCH oi.product p WHERE o.status != com.coffeeshop.entity.OrderStatus.CANCELLED")
    List<OrderItem> findAllCompletedOrderItems();

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN oi.order o WHERE o.user.id = :userId AND o.status != com.coffeeshop.entity.OrderStatus.CANCELLED")
    List<OrderItem> findCompletedOrderItemsByUserId(@Param("userId") UUID userId);

    @Query("SELECT oi.product.id, SUM(oi.quantity) FROM OrderItem oi JOIN oi.order o WHERE o.status != com.coffeeshop.entity.OrderStatus.CANCELLED GROUP BY oi.product.id ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findProductPurchaseTotals();
}
