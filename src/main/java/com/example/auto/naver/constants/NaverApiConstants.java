package com.example.auto.naver.constants;

/**
 * 네이버 커머스 API 상수 클래스
 * 하드코딩된 문자열과 숫자값을 상수로 관리
 */
public final class NaverApiConstants {
    
    private NaverApiConstants() {
        // 인스턴스화 방지
    }
    
    // ========== 상태값 (Status) ==========
    
    /**
     * 판매 상태
     */
    public static final class StatusType {
        public static final String SALE = "SALE"; // 판매중
        public static final String OUTOFSTOCK = "OUTOFSTOCK"; // 품절
        public static final String SUSPENSION = "SUSPENSION"; // 판매중지
        public static final String WAIT = "WAIT"; // 전시 대기
        
        private StatusType() {}
    }
    
    /**
     * 전시 상태
     */
    public static final class DisplayStatusType {
        public static final String ON = "ON"; // 전시 중
        public static final String SUSPENSION = "SUSPENSION"; // 전시 중지
        public static final String WAIT = "WAIT"; // 전시 대기
        
        private DisplayStatusType() {}
    }
    
    /**
     * 배송 방법
     */
    public static final class DeliveryType {
        public static final String DELIVERY = "DELIVERY"; // 택배배송
        public static final String DIRECT = "DIRECT"; // 직접배송
        public static final String QUICK = "QUICK"; // 퀵배송
        
        private DeliveryType() {}
    }
    
    /**
     * 과세 구분
     */
    public static final class TaxType {
        public static final String TAX = "TAX"; // 과세
        public static final String TAX_FREE = "TAX_FREE"; // 면세
        
        private TaxType() {}
    }
    
    /**
     * 판매 유형
     */
    public static final class SaleType {
        public static final String NEW = "NEW"; // 신상품
        public static final String USED = "USED"; // 중고상품
        
        private SaleType() {}
    }
    
    // ========== 기본값 ==========
    
    /**
     * 기본 상태값
     */
    public static final String DEFAULT_STATUS_TYPE = StatusType.SALE;
    public static final String DEFAULT_DISPLAY_STATUS_TYPE = DisplayStatusType.ON;
    public static final String DEFAULT_DELIVERY_TYPE = DeliveryType.DELIVERY;
    public static final String DEFAULT_TAX_TYPE = TaxType.TAX;
    public static final String DEFAULT_SALE_TYPE = SaleType.NEW;
    
    // ========== 배송 관련 상수 ==========
    
    /**
     * 기본 배송비 (원)
     */
    public static final int DEFAULT_DELIVERY_FEE = 4500;
    
    /**
     * 배송비 타입
     */
    public static final class DeliveryFeeType {
        public static final String FREE = "FREE"; // 무료 배송
        public static final String PAID = "PAID"; // 유료 배송
    }
    
    /**
     * 배송비 결제 방식
     */
    public static final class DeliveryFeePayType {
        public static final String PREPAID = "PREPAID"; // 선결제
        public static final String COLLECT = "COLLECT"; // 착불
    }
    
    /**
     * 배송 속성 타입
     */
    public static final String DELIVERY_ATTRIBUTE_TYPE_NORMAL = "NORMAL";
    
    /**
     * 택배사 코드
     */
    public static final class DeliveryCompany {
        public static final String CJGLS = "CJGLS"; // CJ대한통운
        public static final String HANJIN = "HANJIN"; // 한진택배
        public static final String KGB = "KGB"; // 로젠택배
        public static final String HYUNDAI = "HYUNDAI"; // 롯데택배
        public static final String EPOST = "EPOST"; // 우체국택배
    }
    
    // ========== Rate Limit 및 재시도 설정 ==========
    
    /**
     * 이미지 업로드 딜레이 (밀리초)
     */
    public static final int IMAGE_UPLOAD_DELAY_MS = 300;
    
    /**
     * 상품 업로드 딜레이 (밀리초)
     */
    public static final int PRODUCT_UPLOAD_DELAY_MS = 500;
    
    /**
     * 최대 재시도 횟수
     */
    public static final int MAX_RETRIES = 3;
    
    /**
     * 재시도 기본 딜레이 (밀리초)
     */
    public static final long RETRY_BASE_DELAY_MS = 1000L;
    
    // ========== 카테고리 ID 범위 ==========
    
    /**
     * KC 인증이 필요한 카테고리 ID 범위
     */
    public static final class KcCertificationCategoryRange {
        public static final long MIN_1 = 50000003L;
        public static final long MAX_1 = 50002000L;
        public static final long MIN_2 = 50001000L;
        public static final long MAX_2 = 50003000L;
    }
    
    /**
     * 어린이제품 인증이 필요한 카테고리 ID 범위
     */
    public static final class ChildCertificationCategoryRange {
        public static final long MIN_1 = 50004000L;
        public static final long MAX_1 = 50005000L;
        public static final long MIN_2 = 50016000L;
        public static final long MAX_2 = 50017000L;
        public static final long MIN_3 = 50016500L;
        public static final long MAX_3 = 50016700L;
    }
    
    // ========== 원산지 코드 ==========
    
    /**
     * 원산지 코드
     */
    public static final class OriginAreaCode {
        public static final String DOMESTIC = "00"; // 국산
        public static final String IMPORT_START = "02"; // 수입산 시작 코드
        public static final String OTHER = "04"; // 기타-직접 입력
    }
    
    /**
     * 기본 원산지
     */
    public static final String DEFAULT_ORIGIN_AREA = "국내산";
    
    /**
     * 기본 수입사명
     */
    public static final String DEFAULT_IMPORTER = "수입사명";
    
    // ========== 인증 관련 상수 ==========
    
    /**
     * KC 인증 제외 여부
     */
    public static final class KcCertifiedProductExclusion {
        public static final String FALSE = "FALSE"; // KC 인증 대상
        public static final String TRUE = "TRUE"; // KC 인증 대상 아님
        public static final String KC_EXEMPTION_OBJECT = "KC_EXEMPTION_OBJECT"; // 안전기준준수, 구매대행, 병행수입
    }
    
    /**
     * 어린이제품 인증 제외 여부 (boolean)
     */
    public static final boolean DEFAULT_CHILD_CERTIFIED_PRODUCT_EXCLUSION = true;
    
    // ========== 기타 상수 ==========
    
    /**
     * 네이버 쇼핑 등록 기본값
     */
    public static final boolean DEFAULT_NAVER_SHOPPING_REGISTRATION = false;
    
    /**
     * 엑셀 컬럼 너비 (픽셀)
     */
    public static final class ExcelColumnWidth {
        public static final int MIN = 3000;
        public static final int MAX = 15000;
    }
    
    /**
     * 로그 최대 길이 (바이트)
     */
    public static final int MAX_LOG_LINE_LENGTH = 50000;
}

