package com.example.auto.service;

import com.example.auto.dto.GroupProductRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 엑셀 데이터를 GroupProductRequest로 변환하는 컨버터
 * 
 * 주의: 그룹상품은 여러 행이 하나의 그룹을 구성할 수 있습니다.
 * 현재는 단일 행을 그룹상품으로 변환하는 기본 구현입니다.
 * 향후 여러 행을 그룹으로 묶는 기능을 추가할 수 있습니다.
 */
@Slf4j
@Component
public class ExcelToGroupProductConverter {
    
    /**
     * 엑셀 행 데이터를 GroupProductRequest로 변환
     * 
     * 옵션명1, 옵션값1이 있으면 그룹상품으로 처리합니다.
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @return GroupProductRequest 객체
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    public GroupProductRequest convert(Map<String, Object> rowData) {
        if (rowData == null || rowData.isEmpty()) {
            throw new IllegalArgumentException("행 데이터가 비어있습니다.");
        }
        
        Integer rowNumber = (Integer) rowData.get("_rowNumber");
        if (rowNumber == null) {
            rowNumber = 0;
        }
        
        log.debug("엑셀 행 {} 그룹상품 변환 시작", rowNumber);
        
        // 필수 필드: 카테고리
        String leafCategoryId = getStringValue(rowData, "카테고리");
        if (leafCategoryId == null || leafCategoryId.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 카테고리는 필수입니다.", rowNumber));
        }
        
        // 필수 필드: 상품명 (그룹명으로 사용)
        String groupName = getStringValue(rowData, "상품명");
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상품명은 필수입니다.", rowNumber));
        }
        
        // 필수 필드: guideId (판매 옵션 가이드 ID)
        // 엑셀에 guideId 컬럼이 없으면 카테고리로 조회해야 하지만, 
        // 현재는 엑셀에 guideId가 있어야 합니다.
        // 향후 카테고리로 자동 조회하는 기능 추가 가능
        Long guideId = getLongValue(rowData, "guideId");
        if (guideId == null) {
            // guideId가 없으면 에러 (향후 카테고리로 자동 조회 기능 추가 가능)
            throw new IllegalArgumentException(String.format("행 %d: guideId는 필수입니다. 카테고리로 판매 옵션 가이드를 조회하여 guideId를 확인하세요.", rowNumber));
        }
        
        // 판매 옵션 생성
        List<Map<String, Object>> saleOptions = new ArrayList<>();
        
        // 옵션명1, 옵션값1
        String optionName1 = getStringValue(rowData, "옵션명1");
        String optionValue1 = getStringValue(rowData, "옵션값1");
        if (optionName1 != null && !optionName1.trim().isEmpty() && 
            optionValue1 != null && !optionValue1.trim().isEmpty()) {
            Map<String, Object> option1 = new HashMap<>();
            // optionId는 판매 옵션 가이드에서 조회해야 하지만, 
            // 현재는 옵션명으로 optionId를 찾을 수 없으므로
            // valueName만 사용 (향후 개선 필요)
            option1.put("valueName", optionValue1.trim());
            // optionId는 판매 옵션 가이드 API로 조회 필요
            // 임시로 null 또는 빈 값 (네이버 API가 허용하는 경우)
            saleOptions.add(option1);
        }
        
        // 옵션명2, 옵션값2
        String optionName2 = getStringValue(rowData, "옵션명2");
        String optionValue2 = getStringValue(rowData, "옵션값2");
        if (optionName2 != null && !optionName2.trim().isEmpty() && 
            optionValue2 != null && !optionValue2.trim().isEmpty()) {
            Map<String, Object> option2 = new HashMap<>();
            option2.put("valueName", optionValue2.trim());
            saleOptions.add(option2);
        }
        
        if (saleOptions.isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 그룹상품은 최소 1개 이상의 옵션이 필요합니다. (옵션명1, 옵션값1)", rowNumber));
        }
        
        // 필수 필드: 판매가
        BigDecimal salePrice = getBigDecimalValue(rowData, "판매가");
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format("행 %d: 판매가는 필수이며 0보다 커야 합니다.", rowNumber));
        }
        
        // 필수 필드: 재고수량
        Integer stockQuantity = getIntegerValue(rowData, "재고수량");
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(String.format("행 %d: 재고수량은 필수이며 0 이상이어야 합니다.", rowNumber));
        }
        
        // 필수 필드: 상세설명
        String detailContent = getStringValue(rowData, "상세설명");
        if (detailContent == null || detailContent.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상세설명은 필수입니다.", rowNumber));
        }
        
        // 필수 필드: 대표이미지URL
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl == null || mainImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        
        // 이미지 리스트 생성
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(mainImageUrl.trim());
        
        // 추가이미지URL1
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            imageUrls.add(additionalImageUrl1.trim());
        }
        
        // SpecificProduct 생성
        GroupProductRequest.SpecificProduct specificProduct = GroupProductRequest.SpecificProduct.builder()
                .saleOptions(saleOptions)
                .salePrice(salePrice.longValue())
                .stockQuantity(stockQuantity)
                .detailContent(detailContent.trim())
                .imageUrls(imageUrls)
                .build();
        
        // GroupProductRequest 생성
        GroupProductRequest request = GroupProductRequest.builder()
                .groupName(groupName.trim())
                .leafCategoryId(leafCategoryId.trim())
                .guideId(guideId)
                .specificProducts(Arrays.asList(specificProduct))
                .build();
        
        log.debug("엑셀 행 {} 그룹상품 변환 완료: 그룹명={}", rowNumber, groupName);
        
        return request;
    }
    
    /**
     * 여러 행을 하나의 그룹상품으로 묶기
     * 
     * 같은 그룹ID 또는 상품명으로 묶을 수 있습니다.
     * 
     * @param rows 엑셀 행 데이터 리스트
     * @param groupKey 그룹을 구분하는 키 (예: "그룹ID", "상품명")
     * @return 그룹별 GroupProductRequest 리스트
     */
    public List<GroupProductRequest> convertGrouped(List<Map<String, Object>> rows, String groupKey) {
        log.info("그룹상품 변환 시작: 총 {}개 행, 그룹 키={}", rows.size(), groupKey);
        
        // 그룹별로 묶기
        Map<String, List<Map<String, Object>>> groupedRows = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = getStringValue(row, groupKey);
            if (key == null || key.trim().isEmpty()) {
                key = "UNGROUPED_" + row.get("_rowNumber");
            }
            groupedRows.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        
        log.info("그룹화 완료: 총 {}개 그룹", groupedRows.size());
        
        // 각 그룹을 GroupProductRequest로 변환
        List<GroupProductRequest> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedRows.entrySet()) {
            String groupKeyValue = entry.getKey();
            List<Map<String, Object>> groupRows = entry.getValue();
            
            if (groupRows.isEmpty()) {
                continue;
            }
            
            // 첫 번째 행에서 그룹 정보 추출
            Map<String, Object> firstRow = groupRows.get(0);
            
            // 필수 필드 검증
            String leafCategoryId = getStringValue(firstRow, "카테고리");
            String groupName = getStringValue(firstRow, "상품명");
            Long guideId = getLongValue(firstRow, "guideId");
            
            if (leafCategoryId == null || leafCategoryId.trim().isEmpty()) {
                log.warn("그룹 {}: 카테고리가 없어 건너뜁니다.", groupKeyValue);
                continue;
            }
            if (groupName == null || groupName.trim().isEmpty()) {
                log.warn("그룹 {}: 상품명이 없어 건너뜁니다.", groupKeyValue);
                continue;
            }
            if (guideId == null) {
                log.warn("그룹 {}: guideId가 없어 건너뜁니다.", groupKeyValue);
                continue;
            }
            
            // 각 행을 SpecificProduct로 변환
            List<GroupProductRequest.SpecificProduct> specificProducts = new ArrayList<>();
            for (Map<String, Object> row : groupRows) {
                try {
                    GroupProductRequest.SpecificProduct sp = convertToSpecificProduct(row);
                    specificProducts.add(sp);
                } catch (Exception e) {
                    Integer rowNumber = (Integer) row.get("_rowNumber");
                    log.error("그룹 {}: 행 {} 변환 실패: {}", groupKeyValue, rowNumber, e.getMessage());
                    // 개별 행 변환 실패해도 계속 진행
                }
            }
            
            if (specificProducts.isEmpty()) {
                log.warn("그룹 {}: 유효한 상품이 없어 건너뜁니다.", groupKeyValue);
                continue;
            }
            
            // GroupProductRequest 생성
            GroupProductRequest request = GroupProductRequest.builder()
                    .groupName(groupName.trim())
                    .leafCategoryId(leafCategoryId.trim())
                    .guideId(guideId)
                    .specificProducts(specificProducts)
                    .build();
            
            result.add(request);
        }
        
        log.info("그룹상품 변환 완료: 총 {}개 그룹상품", result.size());
        
        return result;
    }
    
    /**
     * 단일 행을 SpecificProduct로 변환
     */
    private GroupProductRequest.SpecificProduct convertToSpecificProduct(Map<String, Object> rowData) {
        Integer rowNumber = (Integer) rowData.get("_rowNumber");
        if (rowNumber == null) {
            rowNumber = 0;
        }
        
        // 판매 옵션 생성
        List<Map<String, Object>> saleOptions = new ArrayList<>();
        
        String optionName1 = getStringValue(rowData, "옵션명1");
        String optionValue1 = getStringValue(rowData, "옵션값1");
        if (optionName1 != null && !optionName1.trim().isEmpty() && 
            optionValue1 != null && !optionValue1.trim().isEmpty()) {
            Map<String, Object> option1 = new HashMap<>();
            option1.put("valueName", optionValue1.trim());
            saleOptions.add(option1);
        }
        
        String optionName2 = getStringValue(rowData, "옵션명2");
        String optionValue2 = getStringValue(rowData, "옵션값2");
        if (optionName2 != null && !optionName2.trim().isEmpty() && 
            optionValue2 != null && !optionValue2.trim().isEmpty()) {
            Map<String, Object> option2 = new HashMap<>();
            option2.put("valueName", optionValue2.trim());
            saleOptions.add(option2);
        }
        
        if (saleOptions.isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 판매 옵션이 필요합니다.", rowNumber));
        }
        
        // 필수 필드
        BigDecimal salePrice = getBigDecimalValue(rowData, "판매가");
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format("행 %d: 판매가는 필수이며 0보다 커야 합니다.", rowNumber));
        }
        
        Integer stockQuantity = getIntegerValue(rowData, "재고수량");
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(String.format("행 %d: 재고수량은 필수이며 0 이상이어야 합니다.", rowNumber));
        }
        
        String detailContent = getStringValue(rowData, "상세설명");
        if (detailContent == null || detailContent.trim().isEmpty()) {
            detailContent = ""; // 그룹상품의 경우 commonDetailContent 사용 가능
        }
        
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl == null || mainImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(mainImageUrl.trim());
        
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            imageUrls.add(additionalImageUrl1.trim());
        }
        
        return GroupProductRequest.SpecificProduct.builder()
                .saleOptions(saleOptions)
                .salePrice(salePrice.longValue())
                .stockQuantity(stockQuantity)
                .detailContent(detailContent.trim())
                .imageUrls(imageUrls)
                .build();
    }
    
    /**
     * Map에서 String 값 추출
     */
    private String getStringValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }
    
    /**
     * Map에서 Integer 값 추출
     */
    private Integer getIntegerValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                if (str.contains(".")) {
                    return (int) Double.parseDouble(str);
                }
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 Integer로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Map에서 Long 값 추출
     */
    private Long getLongValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                if (str.contains(".")) {
                    return (long) Double.parseDouble(str);
                }
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 Long으로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Map에서 BigDecimal 값 추출
     */
    private BigDecimal getBigDecimalValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                str = str.replace(",", "");
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 BigDecimal로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
}

