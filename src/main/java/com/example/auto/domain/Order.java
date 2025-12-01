package com.example.auto.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 엔티티
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;
    
    @Column(nullable = false, unique = true)
    private String orderId; // 네이버 주문 ID
    
    @Column(nullable = false)
    private String orderNumber; // 주문번호
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    
    @Column(nullable = false)
    private String productName; // 주문 시점의 상품명 (스냅샷)
    
    @Column(nullable = false)
    private Integer quantity; // 주문 수량
    
    @Column(nullable = false)
    private BigDecimal unitPrice; // 단가
    
    @Column(nullable = false)
    private BigDecimal totalPrice; // 총 금액
    
    @Column(nullable = false)
    private String buyerName; // 구매자명
    
    @Column(nullable = false)
    private String buyerPhone; // 구매자 전화번호
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status; // 주문상태
    
    private LocalDateTime orderDate; // 주문일시
    
    private LocalDateTime lastSyncedAt; // 마지막 동기화 일시
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderDate == null) {
            orderDate = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum OrderStatus {
        PENDING("주문대기"),
        CONFIRMED("주문확정"),
        PROCESSING("처리중"),
        SHIPPED("배송중"),
        DELIVERED("배송완료"),
        COMPLETED("구매확정"),
        CANCELLED("취소됨");
        
        private final String description;
        
        OrderStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

