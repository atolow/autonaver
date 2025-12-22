package com.example.auto.constants;

import java.util.Arrays;
import java.util.List;

/**
 * 플랫폼 상수 클래스
 * 지원하는 플랫폼 목록과 관련 상수를 관리합니다.
 */
public class PlatformConstants {
    
    /**
     * 지원하는 플랫폼 코드
     */
    public static final String NAVER = "naver";
    public static final String COUPANG = "coupang";
    public static final String ELEVEN_STREET = "11st";
    
    /**
     * 지원하는 모든 플랫폼 목록
     */
    public static final List<String> SUPPORTED_PLATFORMS = Arrays.asList(
            NAVER,
            COUPANG,
            ELEVEN_STREET
    );
    
    /**
     * 플랫폼 목록을 쉼표로 구분한 문자열 (Swagger 문서용)
     */
    public static final String PLATFORMS_DESCRIPTION = String.join(", ", SUPPORTED_PLATFORMS);
    
    /**
     * 플랫폼 목록을 괄호로 감싼 문자열 (오류 메시지용)
     */
    public static final String PLATFORMS_ERROR_MESSAGE = "(" + String.join(", ", SUPPORTED_PLATFORMS) + ")";
    
    /**
     * @Parameter 어노테이션용 플랫폼 설명 (컴파일 타임 상수)
     * 어노테이션 속성은 컴파일 타임 상수여야 하므로 직접 문자열로 정의
     * 
     * 주의: 새 플랫폼 추가 시 이 값도 수동으로 수정해야 합니다.
     * 예: "플랫폼 선택 (naver, coupang, 11st, gmarket)"
     */
    public static final String PARAMETER_DESCRIPTION = "플랫폼 선택 (naver, coupang, 11st)";
    
    /**
     * 플랫폼 설명을 동적으로 생성 (런타임용)
     * SUPPORTED_PLATFORMS를 기반으로 자동 생성됩니다.
     * 
     * @return 플랫폼 선택 설명 문자열
     */
    public static String getParameterDescription() {
        return "플랫폼 선택 " + PLATFORMS_ERROR_MESSAGE;
    }
    
    /**
     * 플랫폼이 지원되는지 확인
     * 
     * @param platform 플랫폼 코드 (대소문자 구분 없음)
     * @return 지원되는 플랫폼이면 true
     */
    public static boolean isSupported(String platform) {
        if (platform == null || platform.trim().isEmpty()) {
            return false;
        }
        return SUPPORTED_PLATFORMS.contains(platform.trim().toLowerCase());
    }
    
    /**
     * 플랫폼 코드를 소문자로 정규화
     * 
     * @param platform 플랫폼 코드
     * @return 정규화된 플랫폼 코드 (소문자)
     */
    public static String normalize(String platform) {
        if (platform == null || platform.trim().isEmpty()) {
            return null;
        }
        return platform.trim().toLowerCase();
    }
    
    /**
     * 플랫폼이 구현되었는지 확인
     * 현재는 네이버만 구현됨
     * 
     * @param platform 플랫폼 코드 (정규화된 소문자)
     * @return 구현된 플랫폼이면 true
     */
    public static boolean isImplemented(String platform) {
        String normalized = normalize(platform);
        return NAVER.equals(normalized);
    }
    
    /**
     * Private constructor to prevent instantiation
     */
    private PlatformConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

