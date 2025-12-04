package com.example.auto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 경로를 네이버 API 카테고리 ID로 변환하는 서비스
 * 
 * 네이버 스마트스토어 카테고리 조회 API를 사용하여 동적으로 카테고리 매핑을 생성합니다.
 * 첫 호출 시 API를 통해 전체 카테고리를 조회하고, 이후에는 캐시된 매핑을 사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryMappingService {
    
    private final ProductService productService;
    
    /**
     * 카테고리 경로 -> 카테고리 ID 매핑 캐시
     * 형식: "대분류 > 중분류 > 소분류" -> "카테고리ID"
     */
    private Map<String, String> categoryMappingCache = null;
    
    /**
     * 카테고리 조회 API를 통해 매핑 테이블 초기화
     * 첫 호출 시에만 API를 호출하고, 이후에는 캐시를 사용합니다.
     */
    private void initializeCategoryMapping() {
        if (categoryMappingCache != null) {
            return; // 이미 초기화됨
        }
        
        try {
            log.info("카테고리 매핑 초기화 시작: 네이버 API에서 카테고리 조회...");
            
            // 현재 스토어의 카테고리 조회 (전체 트리 구조 필요)
            List<Map<String, Object>> categories = productService.getCategories(false);
            
            if (categories != null && !categories.isEmpty()) {
                categoryMappingCache = new HashMap<>();
                
                // 응답 구조 디버깅
                log.debug("카테고리 응답 첫 번째 항목: {}", categories.get(0));
                log.debug("카테고리 응답 첫 번째 항목의 키들: {}", categories.get(0).keySet());
                
                buildCategoryMapping(categories, null, "");
                
                if (categoryMappingCache.isEmpty()) {
                    log.warn("카테고리 매핑이 생성되지 않았습니다. 응답 구조를 확인하세요.");
                    log.warn("첫 번째 카테고리 항목: {}", categories.get(0));
                } else {
                    log.info("카테고리 매핑 초기화 완료: {}개 카테고리 매핑 생성", categoryMappingCache.size());
                    
                    // 전체 카테고리 매핑을 출력 (복사하기 쉽게)
                    // 로그 길이 제한을 피하기 위해 여러 줄로 나누어 출력
                    StringBuilder allCategories = new StringBuilder();
                    allCategories.append("전체 카테고리 매핑 (").append(categoryMappingCache.size()).append("개): ");
                    int count = 0;
                    int lineCount = 0;
                    final int MAX_LINE_LENGTH = 50000; // 한 줄당 최대 길이 (약 50KB)
                    
                    for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                        if (count > 0) {
                            allCategories.append(" | ");
                        }
                        allCategories.append(entry.getKey()).append("=").append(entry.getValue());
                        count++;
                        
                        // 한 줄이 너무 길어지면 출력하고 새 줄 시작
                        if (allCategories.length() > MAX_LINE_LENGTH) {
                            log.info("카테고리 매핑 ({}): {}", lineCount + 1, allCategories.toString());
                            allCategories = new StringBuilder();
                            lineCount++;
                        }
                    }
                    
                    // 마지막 남은 부분 출력
                    if (allCategories.length() > 0) {
                        log.info("카테고리 매핑 ({}): {}", lineCount + 1, allCategories.toString());
                    }
                    
                    log.info("전체 카테고리 매핑 출력 완료: 총 {}개, {}줄", categoryMappingCache.size(), lineCount + 1);
                }
            } else {
                log.warn("카테고리 조회 결과가 비어있습니다. 하드코딩된 매핑을 사용합니다.");
                initializeHardcodedMapping();
            }
        } catch (Exception e) {
            log.error("카테고리 매핑 초기화 실패: {}", e.getMessage(), e);
            log.warn("하드코딩된 매핑을 사용합니다.");
            initializeHardcodedMapping();
        }
    }
    
    /**
     * 카테고리 트리를 순회하며 매핑 테이블 생성
     * 
     * @param categories 카테고리 리스트
     * @param parentPath 부모 카테고리 경로
     * @param parentName 부모 카테고리 이름
     */
    @SuppressWarnings("unchecked")
    private void buildCategoryMapping(List<Map<String, Object>> categories, String parentPath, String parentName) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        
        // 첫 번째 카테고리의 키를 확인하여 응답 구조 파악
        if (categoryMappingCache.isEmpty() && !categories.isEmpty()) {
            Map<String, Object> firstCategory = categories.get(0);
            log.info("카테고리 응답 구조 확인: 첫 번째 카테고리 키들 = {}", firstCategory.keySet());
            log.info("카테고리 응답 샘플 (첫 번째): {}", firstCategory);
            
            // 처음 3개 카테고리 샘플 출력
            for (int i = 0; i < Math.min(3, categories.size()); i++) {
                log.info("카테고리 샘플 {}: {}", i + 1, categories.get(i));
            }
        }
        
        for (Map<String, Object> category : categories) {
            // 카테고리 ID 추출
            Object idObj = category.get("id");
            if (idObj == null) {
                idObj = category.get("categoryId");
            }
            if (idObj == null) {
                idObj = category.get("leafCategoryId");
            }
            if (idObj == null) {
                log.warn("카테고리 ID 필드 누락: category={}", category);
                continue;
            }
            String categoryId = String.valueOf(idObj);
            if ("null".equals(categoryId)) {
                continue;
            }
            
            // wholeCategoryName 사용 (전체 경로)
            // API 응답 구조: wholeCategoryName="패션의류>여성의류>니트/스웨터"
            Object wholeCategoryNameObj = category.get("wholeCategoryName");
            String wholeCategoryPath = null;
            if (wholeCategoryNameObj != null) {
                wholeCategoryPath = String.valueOf(wholeCategoryNameObj);
                // ">"를 " > "로 변환 (엑셀 형식과 일치시키기)
                wholeCategoryPath = wholeCategoryPath.replace(">", " > ");
            }
            
            // last 필드 확인 (리프 카테고리 여부)
            Object lastObj = category.get("last");
            boolean isLeaf = false;
            if (lastObj != null) {
                isLeaf = Boolean.TRUE.equals(lastObj) || "true".equalsIgnoreCase(String.valueOf(lastObj));
            } else {
                // last 필드가 없으면 children으로 판단
                List<Map<String, Object>> children = (List<Map<String, Object>>) category.get("children");
                isLeaf = (children == null || children.isEmpty());
            }
            
            // 리프 카테고리인 경우에만 매핑에 추가
            if (isLeaf) {
                if (wholeCategoryPath != null && !"null".equals(wholeCategoryPath) && !wholeCategoryPath.trim().isEmpty()) {
                    // wholeCategoryName 사용
                    String normalizedPath = wholeCategoryPath.trim();
                    categoryMappingCache.put(normalizedPath, categoryId);
                    
                    // " > " 형식도 추가 (공백 차이 허용)
                    String altPath = normalizedPath.replace(" > ", ">");
                    if (!altPath.equals(normalizedPath)) {
                        categoryMappingCache.put(altPath, categoryId);
                    }
                    
                    if (categoryMappingCache.size() <= 10) {
                        log.info("카테고리 매핑 추가: {} -> {}", normalizedPath, categoryId);
                    }
                } else {
                    // wholeCategoryName이 없으면 name으로 경로 구성 (폴백)
                    Object nameObj = category.get("name");
                    if (nameObj != null) {
                        String categoryName = String.valueOf(nameObj);
                        String currentPath = parentPath == null || parentPath.isEmpty() 
                                ? categoryName.trim() 
                                : (parentPath + " > " + categoryName.trim());
                        categoryMappingCache.put(currentPath, categoryId);
                        if (categoryMappingCache.size() <= 10) {
                            log.warn("wholeCategoryName 없음, name으로 경로 구성: {} -> {}", currentPath, categoryId);
                        }
                    }
                }
            } else {
                // 중간 카테고리: 자식 카테고리 재귀 처리
                List<Map<String, Object>> children = (List<Map<String, Object>>) category.get("children");
                if (children != null && !children.isEmpty()) {
                    // 재귀 호출 시 parentPath 업데이트
                    String nextParentPath = wholeCategoryPath != null && !"null".equals(wholeCategoryPath) 
                            ? wholeCategoryPath.trim() 
                            : (parentPath == null || parentPath.isEmpty() 
                                    ? (category.get("name") != null ? String.valueOf(category.get("name")).trim() : "") 
                                    : (parentPath + " > " + (category.get("name") != null ? String.valueOf(category.get("name")).trim() : "")));
                    buildCategoryMapping(children, nextParentPath, null);
                }
            }
        }
    }
    
    /**
     * 하드코딩된 매핑 테이블 초기화 (API 실패 시 폴백)
     * 기존 하드코딩된 매핑을 유지하여 호환성 보장
     */
    private void initializeHardcodedMapping() {
        categoryMappingCache = new HashMap<>();
        
        // 패션의류
        categoryMappingCache.put("패션의류 > 상의 > 티셔츠", "50000805");
        categoryMappingCache.put("패션의류 > 하의 > 청바지", "50000806");
        categoryMappingCache.put("패션의류 > 하의 > 슬랙스", "50000807");
        
        // 패션잡화
        categoryMappingCache.put("패션잡화 > 가방 > 백팩", "50000808");
        categoryMappingCache.put("패션잡화 > 가방 > 토트백", "50000809");
        
        // 식품
        categoryMappingCache.put("식품 > 과자/간식 > 과자", "50000810");
        categoryMappingCache.put("식품 > 과자/간식 > 초콜릿", "50000811");
        
        // 디지털/가전
        categoryMappingCache.put("디지털/가전 > 모니터", "50000812");
        categoryMappingCache.put("디지털/가전 > 노트북", "50000813");
        
        // 생활/주방
        categoryMappingCache.put("생활/주방 > 주방용품 > 프라이팬", "50000814");
        
        // 반려동물
        categoryMappingCache.put("반려동물 > 강아지용품 > 사료", "50000815");
        
        log.info("하드코딩된 카테고리 매핑 초기화 완료: {}개", categoryMappingCache.size());
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
        
        // 매핑 테이블 초기화 (첫 호출 시에만)
        initializeCategoryMapping();
        
        String trimmedPath = categoryPath.trim();
        
        // 정확한 매칭 시도
        String categoryId = categoryMappingCache.get(trimmedPath);
        if (categoryId != null) {
            log.debug("카테고리 매핑 성공: {} -> {}", trimmedPath, categoryId);
            return categoryId;
        }
        
        // 구분자 형식 변환하여 매칭 시도
        // API는 ">" 형식, 엑셀은 " > " 형식 사용
        // 양쪽 형식 모두 시도
        String[] variations = {
            trimmedPath,  // 원본
            trimmedPath.replaceAll("\\s*>\\s*", ">"),  // " > " -> ">"
            trimmedPath.replaceAll("\\s*>\\s*", " > "),  // ">" -> " > "
            trimmedPath.replace(">", " > "),  // ">" -> " > "
            trimmedPath.replace(" > ", ">")   // " > " -> ">"
        };
        
        for (String variation : variations) {
            categoryId = categoryMappingCache.get(variation);
            if (categoryId != null) {
                log.debug("카테고리 매핑 성공 (구분자 변환): {} -> {} (변환: {})", trimmedPath, categoryId, variation);
                return categoryId;
            }
        }
        
        // 대소문자 무시 매칭 시도
        for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmedPath)) {
                log.debug("카테고리 매핑 성공 (대소문자 무시): {} -> {}", trimmedPath, entry.getValue());
                return entry.getValue();
            }
        }
        
        // 유사한 경로 찾기 (엑셀 경로와 API 경로가 약간 다를 수 있음)
        // 예: 엑셀 "패션의류 > 상의 > 티셔츠" vs API "패션의류>여성의류>티셔츠"
        // 예: 엑셀 "식품 > 과자/간식 > 과자" vs API "식품>과자/베이커리>과자" 등
        String[] searchParts = trimmedPath.split("[>]");
        if (searchParts.length > 0) {
            String lastPart = searchParts[searchParts.length - 1].trim();
            String firstPart = searchParts[0].trim();
            
            // 1단계: 마지막 부분(리프 카테고리)과 첫 부분(최상위 카테고리)이 정확히 일치하는 경로 찾기
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                
                if (entryParts.length > 0) {
                    String entryLastPart = entryParts[entryParts.length - 1].trim();
                    String entryFirstPart = entryParts[0].trim();
                    
                    // 마지막 부분(리프 카테고리)과 첫 부분(최상위 카테고리)이 일치하면 매칭
                    if (entryLastPart.equals(lastPart) && entryFirstPart.equals(firstPart)) {
                        // 경로 단계 수가 같거나 비슷하면 매칭
                        if (entryParts.length == searchParts.length || 
                            (entryParts.length >= searchParts.length - 1 && entryParts.length <= searchParts.length + 1)) {
                            log.info("카테고리 매핑 성공 (유사 경로 매칭): '{}' -> '{}' (ID: {})", 
                                    trimmedPath, entryKey, entry.getValue());
                            return entry.getValue();
                        }
                    }
                }
            }
            
            // 2단계: 첫 부분만 일치하고 마지막 부분이 유사한 경로 찾기
            // 예: "식품 > 과자/간식 > 과자" -> "식품>과자/베이커리>과자" 등
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                
                if (entryParts.length > 0) {
                    String entryLastPart = entryParts[entryParts.length - 1].trim();
                    String entryFirstPart = entryParts[0].trim();
                    
                    // 첫 부분이 일치하고, 마지막 부분이 포함 관계이거나 유사하면 매칭
                    if (entryFirstPart.equals(firstPart) && 
                        (entryLastPart.contains(lastPart) || lastPart.contains(entryLastPart) || 
                         entryLastPart.equalsIgnoreCase(lastPart))) {
                        // 경로 단계 수가 같거나 비슷하면 매칭
                        if (entryParts.length == searchParts.length || 
                            (entryParts.length >= searchParts.length - 1 && entryParts.length <= searchParts.length + 1)) {
                            log.info("카테고리 매핑 성공 (유사 경로 매칭 - 첫/마지막 부분 유사): '{}' -> '{}' (ID: {})", 
                                    trimmedPath, entryKey, entry.getValue());
                            return entry.getValue();
                        }
                    }
                }
            }
            
            // 3단계: 마지막 부분만 일치하는 경우 (최후의 수단)
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                if (entryParts.length > 0 && entryParts[entryParts.length - 1].trim().equals(lastPart)) {
                    log.warn("카테고리 부분 매칭 (마지막 부분만 일치): '{}' -> '{}' (ID: {})", 
                            trimmedPath, entryKey, entry.getValue());
                    return entry.getValue();
                }
            }
            
            // 4단계: 마지막 부분이 포함 관계인 경우
            // 예: "과자" -> "과자/베이커리" 또는 "과자/베이커리" -> "과자"
            for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                String entryKey = entry.getKey();
                String[] entryParts = entryKey.split("[>]");
                if (entryParts.length > 0) {
                    String entryLastPart = entryParts[entryParts.length - 1].trim();
                    // 마지막 부분이 서로 포함 관계이면 매칭
                    if (entryLastPart.contains(lastPart) || lastPart.contains(entryLastPart)) {
                        log.warn("카테고리 부분 매칭 (마지막 부분 포함 관계): '{}' -> '{}' (ID: {})", 
                                trimmedPath, entryKey, entry.getValue());
                        return entry.getValue();
                    }
                }
            }
            
            // 5단계: 첫 부분이 일치하고, 중간/마지막 부분에 키워드가 포함된 경로 찾기
            // 예: "식품 > 밀키트 > 반조리" -> "식품>...>밀키트..." 또는 "식품>...>반조리..."
            if (searchParts.length >= 2) {
                // 검색할 키워드 추출 (중간 부분과 마지막 부분)
                String[] keywords = new String[searchParts.length - 1];
                for (int i = 1; i < searchParts.length; i++) {
                    keywords[i - 1] = searchParts[i].trim().toLowerCase();
                }
                
                for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                    String entryKey = entry.getKey();
                    String[] entryParts = entryKey.split("[>]");
                    
                    if (entryParts.length > 0) {
                        String entryFirstPart = entryParts[0].trim();
                        
                        // 첫 부분이 일치하는 경우
                        if (entryFirstPart.equals(firstPart)) {
                            // 전체 경로를 소문자로 변환하여 키워드 검색
                            String entryKeyLower = entryKey.toLowerCase();
                            boolean allKeywordsFound = true;
                            
                            // 모든 키워드가 경로에 포함되어 있는지 확인
                            for (String keyword : keywords) {
                                if (!entryKeyLower.contains(keyword)) {
                                    allKeywordsFound = false;
                                    break;
                                }
                            }
                            
                            // 모든 키워드가 포함되어 있고, 경로 단계 수가 비슷하면 매칭
                            if (allKeywordsFound && 
                                (entryParts.length == searchParts.length || 
                                 entryParts.length >= searchParts.length - 1 && entryParts.length <= searchParts.length + 1)) {
                                log.info("카테고리 매핑 성공 (키워드 기반 매칭): '{}' -> '{}' (ID: {})", 
                                        trimmedPath, entryKey, entry.getValue());
                                return entry.getValue();
                            }
                        }
                    }
                }
                
                // 6단계: 첫 부분이 일치하고, 일부 키워드만 포함된 경로 찾기 (더 유연한 매칭)
                for (Map.Entry<String, String> entry : categoryMappingCache.entrySet()) {
                    String entryKey = entry.getKey();
                    String[] entryParts = entryKey.split("[>]");
                    
                    if (entryParts.length > 0) {
                        String entryFirstPart = entryParts[0].trim();
                        
                        // 첫 부분이 일치하는 경우
                        if (entryFirstPart.equals(firstPart)) {
                            String entryKeyLower = entryKey.toLowerCase();
                            int matchedKeywords = 0;
                            
                            // 키워드 중 몇 개가 포함되어 있는지 확인
                            for (String keyword : keywords) {
                                if (entryKeyLower.contains(keyword)) {
                                    matchedKeywords++;
                                }
                            }
                            
                            // 키워드의 절반 이상이 포함되어 있고, 경로 단계 수가 비슷하면 매칭
                            if (matchedKeywords >= (keywords.length + 1) / 2 && 
                                (entryParts.length == searchParts.length || 
                                 entryParts.length >= searchParts.length - 1 && entryParts.length <= searchParts.length + 1)) {
                                log.warn("카테고리 매핑 성공 (부분 키워드 매칭, {}/{} 키워드 일치): '{}' -> '{}' (ID: {})", 
                                        matchedKeywords, keywords.length, trimmedPath, entryKey, entry.getValue());
                                return entry.getValue();
                            }
                        }
                    }
                }
            }
        }
        
        // 정확한 매핑이 없으면 null 반환하여 에러 발생시키기
        log.error("카테고리 매핑을 찾을 수 없습니다: '{}'. 엑셀 파일의 카테고리 컬럼을 숫자 ID로 변경하거나, 네이버 스마트스토어에서 확인한 정확한 카테고리 경로를 사용하세요.", trimmedPath);
        log.error("현재 매핑된 카테고리 개수: {}", categoryMappingCache.size());
        
        // 사용 가능한 카테고리 예시를 한 줄로 출력
        StringBuilder examples = new StringBuilder();
        examples.append("사용 가능한 카테고리 예시 (처음 50개): ");
        int count = 0;
        for (String key : categoryMappingCache.keySet()) {
            if (count > 0) {
                examples.append(" | ");
            }
            examples.append(key).append("=").append(categoryMappingCache.get(key));
            count++;
            if (count >= 50) {
                break;
            }
        }
        log.error("{}", examples.toString());
        
        // 검색어와 유사한 카테고리 찾기 (한 줄로)
        StringBuilder similar = new StringBuilder();
        similar.append("검색어 '").append(trimmedPath).append("'와 유사한 카테고리: ");
        String searchLower = trimmedPath.toLowerCase();
        count = 0;
        for (String key : categoryMappingCache.keySet()) {
            if (key.toLowerCase().contains(searchLower) || searchLower.contains(key.toLowerCase())) {
                if (count > 0) {
                    similar.append(", ");
                }
                similar.append(key).append("=").append(categoryMappingCache.get(key));
                count++;
                if (count >= 10) {
                    break;
                }
            }
        }
        if (count > 0) {
            log.error("{}", similar.toString());
        }
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


