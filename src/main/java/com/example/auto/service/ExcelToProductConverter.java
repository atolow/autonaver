package com.example.auto.service;

import com.example.auto.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 엑셀 데이터를 ProductRequest로 변환하는 컨버터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelToProductConverter {
    
    private final CategoryMappingService categoryMappingService;
    
    /**
     * 엑셀 행 데이터를 ProductRequest로 변환
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @return ProductRequest 객체
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    public ProductRequest convert(Map<String, Object> rowData) {
        if (rowData == null || rowData.isEmpty()) {
            throw new IllegalArgumentException("행 데이터가 비어있습니다.");
        }
        
        Integer rowNumber = (Integer) rowData.get("_rowNumber");
        if (rowNumber == null) {
            rowNumber = 0;
        }
        
        log.debug("엑셀 행 {} 변환 시작", rowNumber);
        
        ProductRequest.ProductRequestBuilder builder = ProductRequest.builder();
        
        // 필수 필드: 상품명
        String name = getStringValue(rowData, "상품명");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상품명은 필수입니다.", rowNumber));
        }
        builder.name(name.trim());
        
        // 필수 필드: 카테고리
        String leafCategoryId = getStringValue(rowData, "카테고리");
        if (leafCategoryId == null || leafCategoryId.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 카테고리는 필수입니다.", rowNumber));
        }
        
        String trimmedCategoryId = leafCategoryId.trim();
        
        // 카테고리 경로 저장 (카테고리별 원산지 정보 처리용)
        builder.categoryPath(trimmedCategoryId);
        
        // 숫자 ID인지 확인
        if (categoryMappingService.isNumericCategoryId(trimmedCategoryId)) {
            // 이미 숫자 ID이면 그대로 사용
            builder.leafCategoryId(trimmedCategoryId);
        } else {
            // 한글 경로인 경우 매핑 서비스를 통해 변환 시도
            String mappedCategoryId = categoryMappingService.convertToCategoryId(trimmedCategoryId);
            if (mappedCategoryId != null) {
                log.info("행 {}: 카테고리 경로를 ID로 변환: {} -> {}", rowNumber, trimmedCategoryId, mappedCategoryId);
                builder.leafCategoryId(mappedCategoryId);
            } else {
                // 매핑이 없으면 에러 발생 (잘못된 카테고리 ID로 등록 시도 방지)
                throw new IllegalArgumentException(String.format(
                        "행 %d: 카테고리 매핑을 찾을 수 없습니다: '%s'. " +
                        "엑셀 파일의 카테고리 컬럼을 숫자 ID로 변경하거나, " +
                        "네이버 스마트스토어에서 확인한 정확한 카테고리 경로를 사용하세요.", 
                        rowNumber, trimmedCategoryId));
            }
        }
        
        // 필수 필드: 판매가
        BigDecimal salePrice = getBigDecimalValue(rowData, "판매가");
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format("행 %d: 판매가는 필수이며 0보다 커야 합니다.", rowNumber));
        }
        builder.salePrice(salePrice);
        
        // 필수 필드: 재고수량
        Integer stockQuantity = getIntegerValue(rowData, "재고수량");
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(String.format("행 %d: 재고수량은 필수이며 0 이상이어야 합니다.", rowNumber));
        }
        builder.stockQuantity(stockQuantity);
        
        // 필수 필드: 상세설명
        String detailContent = getStringValue(rowData, "상세설명");
        if (detailContent == null || detailContent.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 상세설명은 필수입니다.", rowNumber));
        }
        builder.detailContent(detailContent.trim());
        
        // 필수 필드: 대표이미지URL
        String mainImageUrl = getStringValue(rowData, "대표이미지URL");
        if (mainImageUrl == null || mainImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("행 %d: 대표이미지URL은 필수입니다.", rowNumber));
        }
        
        // 이미지 리스트 생성
        List<String> images = new ArrayList<>();
        images.add(mainImageUrl.trim());
        
        // 추가이미지URL1
        String additionalImageUrl1 = getStringValue(rowData, "추가이미지URL1");
        if (additionalImageUrl1 != null && !additionalImageUrl1.trim().isEmpty()) {
            images.add(additionalImageUrl1.trim());
        }
        
        builder.images(images);
        
        // 선택 필드: 판매상태 (한글 -> 영문 Enum 변환)
        String statusType = getStringValue(rowData, "판매상태");
        if (statusType != null && !statusType.trim().isEmpty()) {
            builder.statusType(convertStatusType(statusType.trim()));
        } else {
            builder.statusType("SALE"); // 기본값: 판매중
        }
        
        // 선택 필드: 전시상태 (한글 -> 영문 Enum 변환)
        String channelProductDisplayStatusType = getStringValue(rowData, "전시상태");
        if (channelProductDisplayStatusType != null && !channelProductDisplayStatusType.trim().isEmpty()) {
            builder.channelProductDisplayStatusType(convertDisplayStatusType(channelProductDisplayStatusType.trim()));
        } else {
            builder.channelProductDisplayStatusType("ON"); // 기본값: 전시
        }
        
        // 선택 필드: 배송 정보
        ProductRequest.DeliveryInfo deliveryInfo = createDeliveryInfo(rowData, rowNumber);
        if (deliveryInfo != null) {
            builder.deliveryInfo(deliveryInfo);
        }
        
        // 선택 필드: 브랜드
        String brandName = getStringValue(rowData, "브랜드");
        if (brandName != null && !brandName.trim().isEmpty()) {
            builder.brandName(brandName.trim());
        }
        
        // 선택 필드: 모델명
        String modelName = getStringValue(rowData, "모델명");
        if (modelName != null && !modelName.trim().isEmpty()) {
            builder.modelName(modelName.trim());
        }
        
        // 선택 필드: 제조사
        String manufacturer = getStringValue(rowData, "제조사");
        if (manufacturer != null && !manufacturer.trim().isEmpty()) {
            builder.manufacturer(manufacturer.trim());
        }
        
        // 선택 필드: 원산지
        String originArea = getStringValue(rowData, "원산지");
        if (originArea != null && !originArea.trim().isEmpty()) {
            builder.originArea(originArea.trim());
        }
        
        // 선택 필드: 과세구분
        String taxType = getStringValue(rowData, "과세구분");
        if (taxType != null && !taxType.trim().isEmpty()) {
            builder.taxType(taxType.trim().toUpperCase());
        }
        
        ProductRequest request = builder.build();
        log.debug("엑셀 행 {} 변환 완료: 상품명={}", rowNumber, name);
        
        return request;
    }
    
    /**
     * 배송 정보 생성
     */
    private ProductRequest.DeliveryInfo createDeliveryInfo(Map<String, Object> rowData, Integer rowNumber) {
        String deliveryType = getStringValue(rowData, "배송방법");
        String deliveryFeeStr = getStringValue(rowData, "배송비");
        
        // 배송 정보가 없으면 기본값 사용 (ProductService에서 처리)
        if (deliveryType == null && deliveryFeeStr == null) {
            return null;
        }
        
        ProductRequest.DeliveryInfo.DeliveryInfoBuilder builder = ProductRequest.DeliveryInfo.builder();
        
        // 배송방법 (한글 -> 영문 Enum 변환)
        // 엑셀의 "배송방법" 컬럼에 택배사 이름(한진택배, CJ대한통운 등)이 들어올 수 있음
        String finalDeliveryType;
        if (deliveryType != null && !deliveryType.trim().isEmpty()) {
            finalDeliveryType = convertDeliveryType(deliveryType.trim());
            builder.deliveryType(finalDeliveryType);
        } else {
            finalDeliveryType = "DELIVERY"; // 기본값: 택배배송
            builder.deliveryType(finalDeliveryType);
        }
        
        // 택배사 코드 추론 (DELIVERY일 때만)
        if ("DELIVERY".equals(finalDeliveryType)) {
            String deliveryCompany = extractDeliveryCompany(deliveryType);
            if (deliveryCompany != null) {
                builder.deliveryCompany(deliveryCompany);
            } else {
                builder.deliveryCompany("CJGLS"); // 기본값: CJ대한통운
            }
        }
        
        // 배송비
        if (deliveryFeeStr != null && !deliveryFeeStr.trim().isEmpty()) {
            try {
                Integer deliveryFee = Integer.parseInt(deliveryFeeStr.trim());
                builder.deliveryFee(deliveryFee);
            } catch (NumberFormatException e) {
                log.warn("행 {}: 배송비 형식이 올바르지 않습니다: {}", rowNumber, deliveryFeeStr);
            }
        }
        
        return builder.build();
    }
    
    /**
     * 배송방법 문자열에서 택배사 코드 추출
     * 네이버 API 택배사 코드: CJGLS(CJ대한통운), HANJIN(한진택배), KGB(로젠택배), HYUNDAI(롯데택배), EPOST(우체국택배)
     */
    private String extractDeliveryCompany(String deliveryType) {
        if (deliveryType == null || deliveryType.trim().isEmpty()) {
            return null;
        }
        
        String normalized = deliveryType.trim();
        
        // 택배사 이름 -> 택배사 코드 변환
        if (normalized.contains("한진") || normalized.contains("HANJIN")) {
            return "HANJIN";
        } else if (normalized.contains("CJ") || normalized.contains("대한통운") || normalized.contains("CJGLS")) {
            return "CJGLS";
        } else if (normalized.contains("로젠") || normalized.contains("KGB")) {
            return "KGB";
        } else if (normalized.contains("롯데") || normalized.contains("HYUNDAI")) {
            return "HYUNDAI";
        } else if (normalized.contains("우체국") || normalized.contains("EPOST")) {
            return "EPOST";
        }
        
        // 이미 코드 형식이면 그대로 사용
        String upper = normalized.toUpperCase();
        if (upper.equals("CJGLS") || upper.equals("HANJIN") || upper.equals("KGB") || 
            upper.equals("HYUNDAI") || upper.equals("EPOST")) {
            return upper;
        }
        
        return null; // 알 수 없는 값
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
                // 소수점 제거
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
                // 쉼표 제거 (예: "10,000" -> "10000")
                str = str.replace(",", "");
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("행 {}: {} 값을 BigDecimal로 변환 실패: {}", rowData.get("_rowNumber"), key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * 판매상태 한글 값을 영문 Enum 값으로 변환
     * 
     * 중요: 네이버 API 문서에 따르면 상품 등록 시에는 SALE(판매 중)만 입력할 수 있습니다.
     * OUTOFSTOCK(품절)은 재고가 0일 때 시스템이 자동으로 설정하는 상태입니다.
     * 따라서 상품 등록 시에는 항상 SALE을 반환합니다.
     * 
     * 네이버 API Enum 값: SALE(판매중), OUTOFSTOCK(품절), SUSPENSION(판매중지)
     */
    private String convertStatusType(String statusType) {
        if (statusType == null || statusType.trim().isEmpty()) {
            return "SALE";
        }
        
        String normalized = statusType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("판매중") || normalized.equals("판매") || normalized.equalsIgnoreCase("SALE")) {
            return "SALE";
        } else if (normalized.contains("품절") || normalized.equalsIgnoreCase("OUTOFSTOCK") || normalized.equalsIgnoreCase("SOLD_OUT")) {
            // 상품 등록 시에는 OUTOFSTOCK을 사용할 수 없으므로 SALE로 변환
            // 재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정함
            log.warn("상품 등록 시에는 OUTOFSTOCK을 사용할 수 없습니다. SALE로 변환합니다. 재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정합니다.");
            return "SALE";
        } else if (normalized.contains("판매중지") || normalized.contains("중지") || normalized.equalsIgnoreCase("UNSALE") || normalized.equalsIgnoreCase("SUSPENSION")) {
            // 상품 등록 시에는 SUSPENSION을 입력하면 SALE로 등록됨 (API 문서 명시)
            log.warn("상품 등록 시에는 SUSPENSION을 입력하면 SALE로 등록됩니다. SALE로 변환합니다.");
            return "SALE";
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals("SALE")) {
            return "SALE";
        } else if (upper.equals("OUTOFSTOCK") || upper.equals("SUSPENSION") || upper.equals("UNSALE")) {
            // 상품 등록 시에는 SALE만 허용
            log.warn("상품 등록 시에는 {}를 사용할 수 없습니다. SALE로 변환합니다.", upper);
            return "SALE";
        }
        
        // 알 수 없는 값이면 기본값 반환
        log.warn("알 수 없는 판매상태 값: {}, 기본값 SALE 사용", normalized);
        return "SALE";
    }
    
    /**
     * 전시상태 한글 값을 영문 Enum 값으로 변환
     * 
     * 중요: 네이버 API 문서에 따르면 channelProductDisplayStatusType은 ON, SUSPENSION만 입력 가능합니다.
     * 가능한 값: WAIT(전시 대기), ON(전시 중), SUSPENSION(전시 중지)
     * DISPLAY, HIDE, OFF는 유효하지 않은 값입니다.
     */
    private String convertDisplayStatusType(String displayStatusType) {
        if (displayStatusType == null || displayStatusType.trim().isEmpty()) {
            return "ON"; // 기본값: 전시 중
        }
        
        String normalized = displayStatusType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("전시") && !normalized.contains("안함") && !normalized.contains("중지")) {
            return "ON";
        } else if (normalized.contains("전시중지") || normalized.contains("중지") || normalized.contains("SUSPENSION")) {
            return "SUSPENSION";
        } else if (normalized.contains("전시안함") || normalized.contains("안함")) {
            // "전시안함"은 SUSPENSION으로 변환
            return "SUSPENSION";
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals("ON")) {
            return "ON";
        } else if (upper.equals("SUSPENSION") || upper.equals("WAIT")) {
            return upper;
        } else if (upper.equals("OFF") || upper.equals("HIDE") || upper.equals("DISPLAY")) {
            // DISPLAY, HIDE, OFF는 유효하지 않으므로 ON으로 변환
            log.warn("channelProductDisplayStatusType '{}'는 유효하지 않습니다. ON으로 변환합니다. (유효한 값: ON, SUSPENSION)", upper);
            return "ON";
        }
        
        // 알 수 없는 값이면 기본값 반환
        log.warn("알 수 없는 전시상태 값: {}, 기본값 ON 사용 (유효한 값: ON, SUSPENSION)", normalized);
        return "ON";
    }
    
    /**
     * 배송방법 한글 값을 영문 Enum 값으로 변환
     * 네이버 API Enum 값: DELIVERY(택배배송), DIRECT(직접배송), QUICK(퀵배송)
     * 
     * 주의: 엑셀의 "배송방법" 컬럼에 택배사 이름(한진택배, CJ대한통운 등)이 들어올 수 있음
     * 이 경우 모두 "DELIVERY"로 변환
     */
    private String convertDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.trim().isEmpty()) {
            return "DELIVERY";
        }
        
        String normalized = deliveryType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("택배") || normalized.contains("배송") || 
            normalized.contains("한진") || normalized.contains("CJ") || 
            normalized.contains("로젠") || normalized.contains("롯데") ||
            normalized.contains("우체국") || normalized.contains("대한통운") ||
            normalized.equalsIgnoreCase("DELIVERY")) {
            return "DELIVERY";
        } else if (normalized.contains("직접") || normalized.equalsIgnoreCase("DIRECT")) {
            return "DIRECT";
        } else if (normalized.contains("퀵") || normalized.equalsIgnoreCase("QUICK")) {
            return "QUICK";
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals("DELIVERY") || upper.equals("DIRECT") || upper.equals("QUICK")) {
            return upper;
        }
        
        // 알 수 없는 값이면 기본값 반환 (택배사 이름이 들어온 경우도 DELIVERY로 처리)
        log.warn("알 수 없는 배송방법 값: {}, 기본값 DELIVERY 사용", normalized);
        return "DELIVERY";
    }
}

