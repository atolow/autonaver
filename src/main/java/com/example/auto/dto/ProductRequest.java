package com.example.auto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 상품 등록 요청 DTO
 * 네이버 API 형식과 사용자 친화적 형식 모두 지원
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequest {
    
    // 네이버 API 형식: originProduct 객체로 받기
    private OriginProduct originProduct;
    
    // 스마트스토어 채널상품 정보
    private SmartstoreChannelProduct smartstoreChannelProduct;
    
    // 사용자 친화적 형식 (간단한 필드들) - originProduct가 없을 때 사용
    private String name;
    private String leafCategoryId;
    private String detailContent;
    private BigDecimal salePrice;
    private Integer stockQuantity;
    private List<String> images; // 간단한 형식: URL 리스트
    
    // 선택 필드
    private String saleType;
    private String statusType;
    private String channelProductDisplayStatusType;
    private Boolean naverShoppingRegistration;
    private DeliveryInfo deliveryInfo;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OriginProduct {
        private String statusType;
        private String saleType;
        private String leafCategoryId;
        private String name;
        private String detailContent;
        private Map<String, Object> images; // 네이버 API 형식: Object
        private String saleStartDate;
        private String saleEndDate;
        private Long salePrice;
        private Integer stockQuantity;
        private Map<String, Object> deliveryInfo;
        private List<Map<String, Object>> productLogistics;
        private Map<String, Object> detailAttribute;
        private Map<String, Object> customerBenefit;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmartstoreChannelProduct {
        private String channelProductName;
        private Long bbsSeq;
        private Boolean storeKeepExclusiveProduct;
        private Boolean naverShoppingRegistration;
        private String channelProductDisplayStatusType;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeliveryInfo {
        private String deliveryType;
        private String deliveryCompany; // 택배사 코드 (DELIVERY일 때 필수)
        private Integer deliveryFee;
        private Integer freeDeliveryMinAmount;
    }
}

