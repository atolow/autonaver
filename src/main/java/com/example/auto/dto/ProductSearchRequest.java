package com.example.auto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 상품 목록 조회 요청 DTO
 * 네이버 커머스 API v1/products/search 형식
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSearchRequest {
    
    /**
     * 검색 키워드 타입
     * CHANNEL_PRODUCT_NO: 채널 상품번호
     * PRODUCT_NO: 원상품번호
     * GROUP_PRODUCT_NO: 그룹상품번호
     * SELLER_CODE: 판매자 관리 코드
     */
    private String searchKeywordType;
    
    /**
     * 채널 상품번호 목록
     */
    private List<Long> channelProductNos;
    
    /**
     * 원상품번호 목록
     */
    private List<Long> originProductNos;
    
    /**
     * 그룹상품번호 목록
     */
    private List<Long> groupProductNos;
    
    /**
     * 판매자 관리 코드
     */
    private String sellerManagementCode;
    
    /**
     * 상품 판매 상태 목록
     * WAIT, SALE, OUTOFSTOCK, UNADMISSION, REJECTION, SUSPENSION, CLOSE, PROHIBITION, DELETE
     */
    private List<String> productStatusTypes;
    
    /**
     * 페이지 번호 (기본값: 1)
     */
    private Integer page;
    
    /**
     * 페이지 크기 (기본값: 50, 최대: 500)
     */
    private Integer size;
    
    /**
     * 정렬 기준
     * NO, NAME, SELLER_CODE, LOW_PRICE, HIGH_PRICE, REG_DATE, MOD_DATE, SALE_START, SALE_END, 
     * POPULARITY, ACCUMULATE_SALE, LOW_DISCOUNT_PRICE, TOTAL_REVIEW_COUNT, AVERAGE_REVIEW_SCORE
     */
    private String orderType;
    
    /**
     * 검색 기간 유형
     * PROD_REG_DAY, SALE_START_DAY, SALE_END_DAY, PROD_MOD_DAY
     */
    private String periodType;
    
    /**
     * 검색 기간 시작일 (yyyy-MM-dd 형식)
     */
    private String fromDate;
    
    /**
     * 검색 기간 종료일 (yyyy-MM-dd 형식)
     */
    private String toDate;
}

