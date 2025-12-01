package com.example.auto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OAuth 인증 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticateRequest {
    private String type;        // SELF 또는 SELLER
    private String accountId;   // 판매자 ID
    private String vendorId;   // 판매자 ID (선택)
    private String storeName;  // 스토어 이름 (선택)
}

