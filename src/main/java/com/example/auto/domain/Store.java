package com.example.auto.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 네이버 스마트스토어 정보 엔티티
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String vendorId; // 판매자 ID (네이버 스마트스토어 ID)
    
    @Column(nullable = false)
    private String storeName; // 스토어 이름
    
    @Column(length = 1000)
    private String accessToken; // OAuth Access Token
    
    @Column(length = 1000)
    private String refreshToken; // OAuth Refresh Token
    
    private LocalDateTime tokenExpiresAt; // 토큰 만료 시간
    
    private boolean isActive; // 활성화 여부
    
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
}

