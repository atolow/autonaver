package com.example.auto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 엑셀 업로드 결과 리포트 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelUploadResult {
    
    /**
     * 전체 처리된 상품 수
     */
    private int totalCount;
    
    /**
     * 성공한 상품 수
     */
    private int successCount;
    
    /**
     * 실패한 상품 수
     */
    private int failureCount;
    
    /**
     * 성공한 상품 목록 (행 번호와 상품명)
     */
    @Builder.Default
    private List<SuccessItem> successItems = new ArrayList<>();
    
    /**
     * 실패한 상품 목록 (행 번호, 상품명, 에러 메시지)
     */
    @Builder.Default
    private List<FailureItem> failureItems = new ArrayList<>();
    
    /**
     * 성공 CSV 파일 경로
     */
    private String successCsvPath;
    
    /**
     * 실패 CSV 파일 경로
     */
    private String failureCsvPath;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuccessItem {
        /**
         * 엑셀 행 번호 (1부터 시작)
         */
        private Integer rowNumber;
        
        /**
         * 상품명
         */
        private String productName;
        
        /**
         * 네이버 API 응답 (상품 ID 등)
         */
        private Object response;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureItem {
        /**
         * 엑셀 행 번호 (1부터 시작)
         */
        private Integer rowNumber;
        
        /**
         * 상품명 (파싱 가능한 경우)
         */
        private String productName;
        
        /**
         * 에러 메시지
         */
        private String errorMessage;
        
        /**
         * 에러 타입 (VALIDATION_ERROR, API_ERROR 등)
         */
        private String errorType;
    }
}

