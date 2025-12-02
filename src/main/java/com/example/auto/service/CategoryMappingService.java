package com.example.auto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 카테고리 경로를 네이버 API 카테고리 ID로 변환하는 서비스
 * 
 * 주의: 이 매핑은 예시입니다. 실제 네이버 스마트스토어에서 사용하는 정확한 카테고리 ID를 입력해야 합니다.
 * 네이버 스마트스토어 관리자 페이지에서 카테고리 ID를 확인하여 매핑을 업데이트하세요.
 */
@Slf4j
@Service
public class CategoryMappingService {
    
    /**
     * 한글 카테고리 경로를 네이버 API 카테고리 ID로 변환하는 매핑 테이블
     * 
     * 형식: "대분류 > 중분류 > 소분류" -> "카테고리ID"
     * 
     * 주의: 아래 ID들은 예시입니다. 실제 네이버 스마트스토어에서 확인한 정확한 ID로 변경해야 합니다.
     */
    private static final Map<String, String> CATEGORY_MAPPING = new HashMap<>();
    
    static {
        // 패션의류
        CATEGORY_MAPPING.put("패션의류 > 상의 > 티셔츠", "50000805");
        CATEGORY_MAPPING.put("패션의류 > 하의 > 청바지", "50000806");
        CATEGORY_MAPPING.put("패션의류 > 하의 > 슬랙스", "50000807");
        
        // 패션잡화
        CATEGORY_MAPPING.put("패션잡화 > 가방 > 백팩", "50000808");
        CATEGORY_MAPPING.put("패션잡화 > 가방 > 토트백", "50000809");
        
        // 식품
        CATEGORY_MAPPING.put("식품 > 과자/간식 > 과자", "50000810");
        CATEGORY_MAPPING.put("식품 > 과자/간식 > 초콜릿", "50000811");
        
        // 디지털/가전
        CATEGORY_MAPPING.put("디지털/가전 > 모니터", "50000812");
        CATEGORY_MAPPING.put("디지털/가전 > 노트북", "50000813");
        
        // 생활/주방
        CATEGORY_MAPPING.put("생활/주방 > 주방용품 > 프라이팬", "50000814");
        
        // 반려동물
        CATEGORY_MAPPING.put("반려동물 > 강아지용품 > 사료", "50000815");
        
        // TODO: 실제 네이버 스마트스토어에서 사용하는 카테고리 ID로 업데이트 필요
        // 네이버 스마트스토어 관리자 페이지 > 상품관리 > 카테고리 관리에서 확인 가능
    }
    
    /**
     * 한글 카테고리 경로를 네이버 API 카테고리 ID로 변환
     * 
     * @param categoryPath 한글 카테고리 경로 (예: "패션의류 > 상의 > 티셔츠")
     * @return 네이버 API 카테고리 ID (예: "50000805"), 매핑이 없으면 null
     */
    public String convertToCategoryId(String categoryPath) {
        if (categoryPath == null || categoryPath.trim().isEmpty()) {
            return null;
        }
        
        String trimmedPath = categoryPath.trim();
        
        // 정확한 매칭 시도
        String categoryId = CATEGORY_MAPPING.get(trimmedPath);
        if (categoryId != null) {
            log.debug("카테고리 매핑 성공: {} -> {}", trimmedPath, categoryId);
            return categoryId;
        }
        
        // 대소문자 무시 매칭 시도
        for (Map.Entry<String, String> entry : CATEGORY_MAPPING.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedPath)) {
                log.debug("카테고리 매핑 성공 (대소문자 무시): {} -> {}", trimmedPath, entry.getValue());
                return entry.getValue();
            }
        }
        
        // 부분 매칭 시도 (마지막 부분만 매칭)
        String[] parts = trimmedPath.split(">");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1].trim();
            for (Map.Entry<String, String> entry : CATEGORY_MAPPING.entrySet()) {
                if (entry.getKey().endsWith(lastPart)) {
                    log.warn("카테고리 부분 매핑: {} -> {} (정확한 매핑을 추가하세요)", trimmedPath, entry.getValue());
                    return entry.getValue();
                }
            }
        }
        
        log.warn("카테고리 매핑을 찾을 수 없습니다: {}. CategoryMappingService에 매핑을 추가하거나, 엑셀 파일의 카테고리 컬럼을 숫자 ID로 변경하세요.", trimmedPath);
        return null;
    }
    
    /**
     * 카테고리 경로가 숫자 ID인지 확인
     * 
     * @param category 카테고리 경로 또는 ID
     * @return 숫자 ID이면 true, 아니면 false
     */
    public boolean isNumericCategoryId(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        
        try {
            Long.parseLong(category.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

