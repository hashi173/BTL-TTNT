package com.coffeeshop.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a Customer Order.
 * Uses a dual-field design: structured fields (User, grandTotal) for relational integrity, alongside legacy flat fields
 * (customerName, phone, trackingCode) for backward-compatible UI rendering.
 */
@Entity
@Table(name = "orders") // "order" is a reserved keyword in SQL, so we use "orders"
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    // PDF schema fields

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;



    @Column(name = "sub_total", precision = 12, scale = 2)
    private BigDecimal subTotal;

    @Column(name = "grand_total", precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "order_status", length = 50)
    private String orderStatus; // PENDING, COMPLETED, etc. (String version for PDF schema)

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems;

    // Legacy fields for existing UI/controllers

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address_text", columnDefinition = "TEXT")
    private String addressText; // free-form address string (legacy)

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "tracking_code", unique = true)
    private String trackingCode;

    @Column(name = "order_type")
    private String orderType;

    // Convenience accessors

    /** Alias for legacy code that calls getOrderDetails() */
    @Transient
    public List<OrderItem> getOrderDetails() {
        return orderItems;
    }

    /** Legacy setter: maps "address" string to "addressText" to avoid clash with UserAddress field */
    public void setAddress(String address) {
        this.addressText = address;
    }

    /** Legacy getter: maps "address" string from "addressText" */
    public String getAddress() {
        return this.addressText;
    }

    @Transient
    public String getDisplayCode() {
        if (StringUtils.hasText(trackingCode)) {
            return trackingCode;
        }
        if (getId() == null) {
            return "ORD-DRAFT";
        }
        return "ORD-" + getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
