package com.coffeeshop.repository;

import com.coffeeshop.entity.Order;
import com.coffeeshop.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {



    long countByStatus(OrderStatus status);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = :status")
    Double sumTotalAmountByStatus(@Param("status") OrderStatus status);

    java.util.Optional<Order> findByTrackingCode(String trackingCode);





    @Query("SELECT i.snapshotProductName, SUM(i.quantity), MAX(p.image) FROM OrderItem i JOIN i.order o LEFT JOIN i.product p WHERE o.status = com.coffeeshop.entity.OrderStatus.COMPLETED GROUP BY i.snapshotProductName ORDER BY SUM(i.quantity) DESC")
    List<Object[]> findTopSellingProducts(Pageable pageable);

    @Query("SELECT YEAR(o.createdAt) as year, MONTH(o.createdAt) as month, SUM(o.totalAmount) as total FROM Order o WHERE o.status = com.coffeeshop.entity.OrderStatus.COMPLETED GROUP BY YEAR(o.createdAt), MONTH(o.createdAt) ORDER BY year DESC, month DESC")
    List<Object[]> findMonthlyRevenue();

    @Query("SELECT i.snapshotProductName, SUM(i.quantity), MAX(p.image) " +
            "FROM OrderItem i " +
            "JOIN i.order o " +
            "LEFT JOIN i.product p " +
            "WHERE o.status = com.coffeeshop.entity.OrderStatus.COMPLETED AND MONTH(o.createdAt) = :month AND YEAR(o.createdAt) = :year " +
            "GROUP BY i.snapshotProductName " +
            "ORDER BY SUM(i.quantity) DESC")
    List<Object[]> findTopSellingProductByMonth(@Param("month") int month, @Param("year") int year,
            Pageable pageable);

    List<Order> findAllByStatusAndCreatedAtBetween(OrderStatus status, java.time.LocalDateTime start,
            java.time.LocalDateTime end);

    Page<Order> findAllByStatusAndCreatedAtBetween(OrderStatus status,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end, Pageable pageable);

    List<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findAllByStatusInOrderByCreatedAtDesc(List<OrderStatus> statuses);





    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByStatusIn(List<OrderStatus> statuses, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE " +
            "LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(o.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(o.trackingCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%')")
    Page<Order> searchOrdersPaginated(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE " +
            "(LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(o.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(o.trackingCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%')) AND " +
            "o.status = :status")
    Page<Order> searchOrdersAndStatusPaginated(@Param("keyword") String keyword,
            @Param("status") OrderStatus status, Pageable pageable);

    Page<Order> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
