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
    
    // 엑셀 업로드용 추가 필드
    private String brandName; // 브랜드
    private String modelName; // 모델명 (상품정보제공고시)
    private String manufacturer; // 제조사 (상품정보제공고시)
    private String originArea; // 원산지 (예: "국내산", "중국산" 등)
    private String taxType; // 과세구분 (TAX, TAX_FREE 등)
    private String categoryPath; // 카테고리 경로 (예: "식품 > 과자/간식 > 과자") - 카테고리별 원산지 정보 처리용
    
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

