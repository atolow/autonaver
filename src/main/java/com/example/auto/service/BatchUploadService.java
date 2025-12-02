package com.example.auto.service;

import com.example.auto.dto.ExcelUploadResult;
import com.example.auto.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 배치 업로드 서비스
 * 엑셀 파일에서 읽은 여러 상품을 순차적으로 네이버 스토어에 업로드
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchUploadService {
    
    private final ExcelService excelService;
    private final ExcelToProductConverter excelToProductConverter;
    private final ProductService productService;
    private final CsvExportService csvExportService;
    
    /**
     * 엑셀 파일을 읽어서 상품들을 배치로 업로드
     * 
     * @param storeId 스토어 ID
     * @param excelRows 엑셀 행 데이터 리스트
     * @return 업로드 결과 리포트
     */
    public ExcelUploadResult batchUploadProducts(Long storeId, List<Map<String, Object>> excelRows) {
        log.info("배치 업로드 시작: 스토어 ID={}, 총 {}개 행", storeId, excelRows.size());
        
        ExcelUploadResult.ExcelUploadResultBuilder resultBuilder = ExcelUploadResult.builder()
                .totalCount(excelRows.size())
                .successCount(0)
                .failureCount(0);
        
        List<ExcelUploadResult.SuccessItem> successItems = new ArrayList<>();
        List<ExcelUploadResult.FailureItem> failureItems = new ArrayList<>();
        
        // 원본 엑셀 데이터를 Map으로 저장 (CSV 저장용)
        Map<Integer, Map<String, Object>> originalDataMap = new java.util.LinkedHashMap<>();
        
        // 각 행을 순차적으로 처리
        for (Map<String, Object> rowData : excelRows) {
            Integer rowNumber = (Integer) rowData.get("_rowNumber");
            if (rowNumber == null) {
                rowNumber = 0;
            }
            
            // 원본 데이터 저장 (CSV 저장용)
            originalDataMap.put(rowNumber, new java.util.LinkedHashMap<>(rowData));
            
            String productName = null;
            try {
                // 엑셀 행 데이터를 ProductRequest로 변환
                ProductRequest productRequest = excelToProductConverter.convert(rowData);
                productName = productRequest.getName();
                
                log.info("상품 업로드 시작: 행 {}, 상품명={}", rowNumber, productName);
                
                // Rate Limit 방지를 위해 상품 업로드 사이에 딜레이 추가 (첫 번째 상품 제외)
                if (rowNumber > 1) {
                    try {
                        Thread.sleep(500); // 500ms 딜레이 (Rate Limit 방지)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("상품 업로드 딜레이 중단됨");
                    }
                }
                
                // 네이버 스토어에 상품 등록
                Map<String, Object> response = productService.createProduct(storeId, productRequest);
                
                // 성공 처리
                ExcelUploadResult.SuccessItem successItem = ExcelUploadResult.SuccessItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName)
                        .response(response)
                        .build();
                successItems.add(successItem);
                
                log.info("상품 업로드 성공: 행 {}, 상품명={}", rowNumber, productName);
                
            } catch (IllegalArgumentException e) {
                // 검증 에러
                log.error("상품 업로드 실패 (검증 에러): 행 {}, 상품명={}, 에러={}", rowNumber, productName, e.getMessage());
                
                ExcelUploadResult.FailureItem failureItem = ExcelUploadResult.FailureItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName != null ? productName : "파싱 실패")
                        .errorMessage(e.getMessage())
                        .errorType("VALIDATION_ERROR")
                        .build();
                failureItems.add(failureItem);
                
            } catch (Exception e) {
                // 기타 에러 (API 에러 등)
                log.error("상품 업로드 실패: 행 {}, 상품명={}", rowNumber, productName, e);
                
                ExcelUploadResult.FailureItem failureItem = ExcelUploadResult.FailureItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName != null ? productName : "파싱 실패")
                        .errorMessage(e.getMessage() != null ? e.getMessage() : "알 수 없는 오류")
                        .errorType("API_ERROR")
                        .build();
                failureItems.add(failureItem);
            }
        }
        
        ExcelUploadResult result = resultBuilder
                .successCount(successItems.size())
                .failureCount(failureItems.size())
                .successItems(successItems)
                .failureItems(failureItems)
                .build();
        
        log.info("배치 업로드 완료: 스토어 ID={}, 총 {}개, 성공 {}개, 실패 {}개", 
                storeId, result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        
        // CSV 파일 저장
        String successCsvPath = null;
        String failureCsvPath = null;
        try {
            successCsvPath = csvExportService.exportSuccessToCsv(successItems, originalDataMap);
            failureCsvPath = csvExportService.exportFailureToCsv(failureItems, originalDataMap);
            
            if (successCsvPath != null) {
                log.info("성공 CSV 파일 저장됨: {}", successCsvPath);
            }
            if (failureCsvPath != null) {
                log.info("실패 CSV 파일 저장됨: {}", failureCsvPath);
            }
        } catch (Exception e) {
            log.error("CSV 파일 저장 중 오류 발생", e);
        }
        
        // 결과에 CSV 파일 경로 추가
        result.setSuccessCsvPath(successCsvPath);
        result.setFailureCsvPath(failureCsvPath);
        
        return result;
    }
}

