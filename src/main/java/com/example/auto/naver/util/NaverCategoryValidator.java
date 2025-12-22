package com.example.auto.naver.util;

import com.example.auto.naver.constants.NaverApiConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 카테고리 검증 및 판단 유틸리티
 * 카테고리별 특수 요구사항(인증 등)을 판단
 */
@Slf4j
public final class NaverCategoryValidator {
    
    private NaverCategoryValidator() {
        // 인스턴스화 방지
    }
    
    /**
     * KC 인증이 필요한 카테고리인지 확인
     * 
     * @param categoryPath 카테고리 경로 (예: "디지털/가전 > 모니터")
     * @param leafCategoryId 카테고리 ID (예: "50000153")
     * @return KC 인증 필요 여부
     */
    public static boolean isKcCertificationRequired(String categoryPath, String leafCategoryId) {
        // 카테고리 경로로 확인 (더 정확하게)
        if (categoryPath != null && !categoryPath.trim().isEmpty()) {
            String lowerCategoryPath = categoryPath.toLowerCase();
            // KC 인증이 필요한 카테고리 키워드 확인 (디지털/가전 카테고리만)
            if ((lowerCategoryPath.contains("디지털") || lowerCategoryPath.contains("가전")) &&
                (lowerCategoryPath.contains("모니터") || lowerCategoryPath.contains("노트북") ||
                 lowerCategoryPath.contains("pc") || lowerCategoryPath.contains("컴퓨터") ||
                 lowerCategoryPath.contains("tv") || lowerCategoryPath.contains("스마트폰") ||
                 lowerCategoryPath.contains("태블릿") || lowerCategoryPath.contains("카메라") ||
                 lowerCategoryPath.contains("전기") || lowerCategoryPath.contains("전자"))) {
                log.info("KC 인증 필요 카테고리 감지 (경로): {}", categoryPath);
                return true;
            }
        }
        
        // 카테고리 ID로 확인 (더 정확한 범위)
        if (leafCategoryId != null) {
            try {
                long categoryIdNum = Long.parseLong(leafCategoryId);
                // 디지털/가전 카테고리 ID 범위 확인
                if ((categoryIdNum >= NaverApiConstants.KcCertificationCategoryRange.MIN_1 && 
                     categoryIdNum <= NaverApiConstants.KcCertificationCategoryRange.MAX_1) ||
                    (categoryIdNum >= NaverApiConstants.KcCertificationCategoryRange.MIN_2 && 
                     categoryIdNum <= NaverApiConstants.KcCertificationCategoryRange.MAX_2)) {
                    log.info("KC 인증 필요 카테고리 감지 (ID): {}", leafCategoryId);
                    return true;
                }
            } catch (NumberFormatException e) {
                // 카테고리 ID가 숫자가 아니면 무시
            }
        }
        
        return false;
    }
    
    /**
     * 어린이제품 인증이 필요한 카테고리인지 확인
     * 
     * @param categoryPath 카테고리 경로 (예: "출산/육아 > 완구")
     * @param leafCategoryId 카테고리 ID (예: "50004000")
     * @return 어린이제품 인증 필요 여부
     */
    public static boolean isChildCertificationRequired(String categoryPath, String leafCategoryId) {
        // 카테고리 경로로 확인
        if (categoryPath != null && !categoryPath.trim().isEmpty()) {
            String lowerCategoryPath = categoryPath.toLowerCase();
            // 어린이제품 인증이 필요한 카테고리 키워드 확인
            if (lowerCategoryPath.contains("출산") || lowerCategoryPath.contains("육아") ||
                lowerCategoryPath.contains("완구") || lowerCategoryPath.contains("인형") ||
                lowerCategoryPath.contains("유아") || lowerCategoryPath.contains("아동") ||
                lowerCategoryPath.contains("어린이") || lowerCategoryPath.contains("키즈") ||
                lowerCategoryPath.contains("장난감") || lowerCategoryPath.contains("아기")) {
                log.info("어린이제품 인증 필요 카테고리 감지 (경로): {}", categoryPath);
                return true;
            }
        }
        
        // 카테고리 ID로 확인 (출산/육아 카테고리 ID 범위)
        if (leafCategoryId != null) {
            try {
                long categoryIdNum = Long.parseLong(leafCategoryId);
                // 출산/육아 카테고리 ID 범위 확인
                if ((categoryIdNum >= NaverApiConstants.ChildCertificationCategoryRange.MIN_1 && 
                     categoryIdNum <= NaverApiConstants.ChildCertificationCategoryRange.MAX_1) ||
                    (categoryIdNum >= NaverApiConstants.ChildCertificationCategoryRange.MIN_2 && 
                     categoryIdNum <= NaverApiConstants.ChildCertificationCategoryRange.MAX_2) ||
                    (categoryIdNum >= NaverApiConstants.ChildCertificationCategoryRange.MIN_3 && 
                     categoryIdNum <= NaverApiConstants.ChildCertificationCategoryRange.MAX_3)) {
                    log.info("어린이제품 인증 필요 카테고리 감지 (ID): {}", leafCategoryId);
                    return true;
                }
            } catch (NumberFormatException e) {
                // 카테고리 ID가 숫자가 아니면 무시
            }
        }
        
        return false;
    }
    
    /**
     * 해산물 카테고리인지 확인
     * 
     * @param categoryPath 카테고리 경로 (예: "식품 > 해산물 > 생선")
     * @return 해산물 카테고리 여부
     */
    public static boolean isMarineCategory(String categoryPath) {
        if (categoryPath == null || categoryPath.trim().isEmpty()) {
            return false;
        }
        
        String trimmedCategoryPath = categoryPath.trim();
        
        // 숫자 ID인지 확인 (카테고리 ID는 보통 숫자)
        boolean isNumericCategoryId = trimmedCategoryPath.matches("^\\d+$");
        
        if (isNumericCategoryId) {
            // 숫자 ID인 경우 해산물 여부를 알 수 없으므로 안전하게 false로 설정
            log.info("카테고리 ID가 숫자입니다. 해산물 여부를 판단할 수 없어 안전하게 false로 설정: categoryId={}", 
                    trimmedCategoryPath);
            return false;
        }
        
        // 한글 카테고리 경로인 경우에만 해산물 여부 판단
        String lowerCategoryPath = trimmedCategoryPath.toLowerCase();
        boolean isMarine = lowerCategoryPath.contains("해산물") || 
                          lowerCategoryPath.contains("수산물") ||
                          lowerCategoryPath.contains("어류") ||
                          lowerCategoryPath.contains("조개류");
        
        if (isMarine) {
            log.info("카테고리 경로 분석: categoryPath={}, isMarineCategory=true", trimmedCategoryPath);
        }
        
        return isMarine;
    }
}

