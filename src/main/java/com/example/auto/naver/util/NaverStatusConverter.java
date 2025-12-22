package com.example.auto.naver.util;

import com.example.auto.naver.constants.NaverApiConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 API 상태값 변환 유틸리티
 * 한글 상태값을 네이버 API 영문 Enum 값으로 변환
 */
@Slf4j
public final class NaverStatusConverter {
    
    private NaverStatusConverter() {
        // 인스턴스화 방지
    }
    
    /**
     * 판매상태 한글 값을 영문 Enum 값으로 변환
     * 
     * 중요: 네이버 API 문서에 따르면 상품 등록 시에는 SALE(판매 중)만 입력할 수 있습니다.
     * OUTOFSTOCK(품절)은 재고가 0일 때 시스템이 자동으로 설정하는 상태입니다.
     * 따라서 상품 등록 시에는 항상 SALE을 반환합니다.
     * 
     * 네이버 API Enum 값: SALE(판매중), OUTOFSTOCK(품절), SUSPENSION(판매중지)
     * 
     * @param statusType 판매상태 (한글 또는 영문)
     * @return 네이버 API 판매상태 값 (항상 SALE)
     */
    public static String convertStatusType(String statusType) {
        if (statusType == null || statusType.trim().isEmpty()) {
            return NaverApiConstants.DEFAULT_STATUS_TYPE;
        }
        
        String normalized = statusType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("판매중") || normalized.equals("판매") || normalized.equalsIgnoreCase(NaverApiConstants.StatusType.SALE)) {
            return NaverApiConstants.StatusType.SALE;
        } else if (normalized.contains("품절") || normalized.equalsIgnoreCase(NaverApiConstants.StatusType.OUTOFSTOCK) || normalized.equalsIgnoreCase("SOLD_OUT")) {
            // 상품 등록 시에는 OUTOFSTOCK을 사용할 수 없으므로 SALE로 변환
            // 재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정함
            log.warn("상품 등록 시에는 OUTOFSTOCK을 사용할 수 없습니다. SALE로 변환합니다. 재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정합니다.");
            return NaverApiConstants.StatusType.SALE;
        } else if (normalized.contains("판매중지") || normalized.contains("중지") || normalized.equalsIgnoreCase(NaverApiConstants.StatusType.SUSPENSION)) {
            // 상품 등록 시에는 SUSPENSION을 입력하면 SALE로 등록됨 (API 문서 명시)
            log.warn("상품 등록 시에는 SUSPENSION을 입력하면 SALE로 등록됩니다. SALE로 변환합니다.");
            return NaverApiConstants.StatusType.SALE;
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals(NaverApiConstants.StatusType.SALE)) {
            return NaverApiConstants.StatusType.SALE;
        } else if (upper.equals(NaverApiConstants.StatusType.OUTOFSTOCK) || 
                   upper.equals(NaverApiConstants.StatusType.SUSPENSION) || 
                   upper.equals("UNSALE")) {
            // 상품 등록 시에는 SALE만 허용
            log.warn("상품 등록 시에는 {}를 사용할 수 없습니다. SALE로 변환합니다.", upper);
            return NaverApiConstants.StatusType.SALE;
        }
        
        // 알 수 없는 값이면 기본값 반환
        log.warn("알 수 없는 판매상태 값: {}, 기본값 SALE 사용", normalized);
        return NaverApiConstants.DEFAULT_STATUS_TYPE;
    }
    
    /**
     * 전시상태 한글 값을 영문 Enum 값으로 변환
     * 
     * 중요: 네이버 API 문서에 따르면 channelProductDisplayStatusType은 ON, SUSPENSION만 입력 가능합니다.
     * 가능한 값: WAIT(전시 대기), ON(전시 중), SUSPENSION(전시 중지)
     * DISPLAY, HIDE, OFF는 유효하지 않은 값입니다.
     * 
     * @param displayStatusType 전시상태 (한글 또는 영문)
     * @return 네이버 API 전시상태 값
     */
    public static String convertDisplayStatusType(String displayStatusType) {
        if (displayStatusType == null || displayStatusType.trim().isEmpty()) {
            return NaverApiConstants.DEFAULT_DISPLAY_STATUS_TYPE;
        }
        
        String normalized = displayStatusType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("전시") && !normalized.contains("안함") && !normalized.contains("중지")) {
            return NaverApiConstants.DisplayStatusType.ON;
        } else if (normalized.contains("전시중지") || normalized.contains("중지") || normalized.contains(NaverApiConstants.DisplayStatusType.SUSPENSION)) {
            return NaverApiConstants.DisplayStatusType.SUSPENSION;
        } else if (normalized.contains("전시안함") || normalized.contains("안함")) {
            // "전시안함"은 SUSPENSION으로 변환
            return NaverApiConstants.DisplayStatusType.SUSPENSION;
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals(NaverApiConstants.DisplayStatusType.ON)) {
            return NaverApiConstants.DisplayStatusType.ON;
        } else if (upper.equals(NaverApiConstants.DisplayStatusType.SUSPENSION) || upper.equals(NaverApiConstants.DisplayStatusType.WAIT)) {
            return upper;
        } else if (upper.equals("OFF") || upper.equals("HIDE") || upper.equals("DISPLAY")) {
            // DISPLAY, HIDE, OFF는 유효하지 않으므로 ON으로 변환
            log.warn("channelProductDisplayStatusType '{}'는 유효하지 않습니다. ON으로 변환합니다. (유효한 값: ON, SUSPENSION)", upper);
            return NaverApiConstants.DisplayStatusType.ON;
        }
        
        // 알 수 없는 값이면 기본값 반환
        log.warn("알 수 없는 전시상태 값: {}, 기본값 ON 사용 (유효한 값: ON, SUSPENSION)", normalized);
        return NaverApiConstants.DEFAULT_DISPLAY_STATUS_TYPE;
    }
    
    /**
     * 배송방법 한글 값을 영문 Enum 값으로 변환
     * 네이버 API Enum 값: DELIVERY(택배배송), DIRECT(직접배송), QUICK(퀵배송)
     * 
     * 주의: 엑셀의 "배송방법" 컬럼에 택배사 이름(한진택배, CJ대한통운 등)이 들어올 수 있음
     * 이 경우 모두 "DELIVERY"로 변환
     * 
     * @param deliveryType 배송방법 (한글 또는 영문)
     * @return 네이버 API 배송방법 값
     */
    public static String convertDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.trim().isEmpty()) {
            return NaverApiConstants.DEFAULT_DELIVERY_TYPE;
        }
        
        String normalized = deliveryType.trim();
        
        // 한글 값 -> 영문 Enum 값
        if (normalized.contains("택배") || normalized.contains("배송") || 
            normalized.contains("한진") || normalized.contains("CJ") || 
            normalized.contains("로젠") || normalized.contains("롯데") ||
            normalized.contains("우체국") || normalized.contains("대한통운") ||
            normalized.equalsIgnoreCase(NaverApiConstants.DeliveryType.DELIVERY)) {
            return NaverApiConstants.DeliveryType.DELIVERY;
        } else if (normalized.contains("직접") || normalized.equalsIgnoreCase(NaverApiConstants.DeliveryType.DIRECT)) {
            return NaverApiConstants.DeliveryType.DIRECT;
        } else if (normalized.contains("퀵") || normalized.equalsIgnoreCase(NaverApiConstants.DeliveryType.QUICK)) {
            return NaverApiConstants.DeliveryType.QUICK;
        }
        
        // 이미 영문 Enum 값이면 그대로 사용 (대문자 변환)
        String upper = normalized.toUpperCase();
        if (upper.equals(NaverApiConstants.DeliveryType.DELIVERY) || 
            upper.equals(NaverApiConstants.DeliveryType.DIRECT) || 
            upper.equals(NaverApiConstants.DeliveryType.QUICK)) {
            return upper;
        }
        
        // 알 수 없는 값이면 기본값 반환 (택배사 이름이 들어온 경우도 DELIVERY로 처리)
        log.warn("알 수 없는 배송방법 값: {}, 기본값 DELIVERY 사용", normalized);
        return NaverApiConstants.DEFAULT_DELIVERY_TYPE;
    }
}

