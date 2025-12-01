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
 * 상품 엔티티
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;
    
    @Column(nullable = false)
    private String productId; // 네이버 상품 ID
    
    @Column(nullable = false)
    private String productName;
    
    @Column(length = 2000)
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    private BigDecimal salePrice; // 할인가
    
    private Integer stock; // 재고 수량
    
    @Column(length = 1000)
    private String imageUrl;
    
    @Column(length = 1000)
    private String productUrl; // 상품 페이지 URL
    
    @Enumerated(EnumType.STRING)
    private ProductStatus status; // 판매중, 품절, 판매중지 등
    
    private LocalDateTime lastSyncedAt; // 마지막 동기화 일시
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ProductStatus {
        ON_SALE("판매중"),
        OUT_OF_STOCK("품절"),
        SALE_STOPPED("판매중지");
        
        private final String description;
        
        ProductStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

