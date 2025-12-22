package com.example.auto.naver.service;

import com.example.auto.naver.constants.NaverApiConstants;
import com.example.auto.naver.util.NaverCategoryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 네이버 원산지 정보 처리 서비스
 * 카테고리별 원산지 정보를 자동 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverOriginAreaService {
    
    /**
     * 카테고리별 원산지 정보를 자동 생성합니다.
     * 카테고리와 원산지에 따라 필요한 필드를 자동으로 추가합니다.
     * 
     * @param originArea 원산지 (예: "국내산", "중국", "일본" 등)
     * @param categoryPath 카테고리 경로 (예: "식품 > 과자/간식 > 과자")
     * @param originNameToCodeMap 원산지 이름-코드 매핑 (선택적, null 가능)
     * @return 원산지 정보 Map
     */
    public Map<String, Object> createOriginAreaInfo(String originArea, String categoryPath, Map<String, String> originNameToCodeMap) {
        Map<String, Object> originAreaInfo = new HashMap<>();
        
        // 원산지 기본 정보 설정
        if (originArea != null && !originArea.trim().isEmpty()) {
            String trimmedOriginArea = originArea.trim();
            originAreaInfo.put("content", trimmedOriginArea);
            
            // 원산지 코드 추론 (원산지 코드 조회 API를 통해 구체적인 국가 코드 찾기)
            // 원산지 코드 조회 API가 호출되었는지 확인하고, 매핑이 있으면 사용
            String originAreaCode = null;
            if (originNameToCodeMap != null && !originNameToCodeMap.isEmpty()) {
                String lowerCaseOrigin = trimmedOriginArea.toLowerCase();
                
                // 정확히 일치하는 경우
                if (originNameToCodeMap.containsKey(lowerCaseOrigin)) {
                    originAreaCode = originNameToCodeMap.get(lowerCaseOrigin);
                    log.info("원산지 코드 매핑 발견: {} -> {}", trimmedOriginArea, originAreaCode);
                } else {
                    // 부분 일치 검색 (예: "베트남"이 포함된 경우)
                    for (Map.Entry<String, String> entry : originNameToCodeMap.entrySet()) {
                        String key = entry.getKey();
                        if (key.contains(lowerCaseOrigin) || lowerCaseOrigin.contains(key)) {
                            originAreaCode = entry.getValue();
                            log.info("원산지 코드 부분 일치 발견: {} -> {} (매핑 키: {})", trimmedOriginArea, originAreaCode, key);
                            break;
                        }
                    }
                }
            }
            
            // 매핑을 찾지 못한 경우 기본 로직 사용
            if (originAreaCode == null) {
                originAreaCode = determineOriginAreaCode(trimmedOriginArea);
                log.debug("원산지 코드 매핑을 찾지 못함: {}. 기본 로직 사용: {}", trimmedOriginArea, originAreaCode);
            }
            
            // originAreaCode는 필수 필드입니다 (네이버 API 요구사항)
            originAreaInfo.put("originAreaCode", originAreaCode);
            log.debug("originAreaCode 설정: originArea={}, originAreaCode={}", trimmedOriginArea, originAreaCode);
            
            // 해외 원산지인지 확인 (수입산: "02" 또는 "02"로 시작하는 코드)
            boolean isForeignOrigin = originAreaCode.startsWith(NaverApiConstants.OriginAreaCode.IMPORT_START);
            
            // 카테고리별 특수 필드 처리 (해산물 여부 판단)
            boolean isMarineCategory = NaverCategoryValidator.isMarineCategory(categoryPath);
            
            // 해외 원산지인 경우 수입사 필수 필드 채우기
            // API 문서: originAreaCode=02 (수입산)일 때 importer 필드가 필수
            // 주의: API 문서에는 importCountry 필드가 없지만, 에러 메시지에서 요구할 수 있음
            // 일단 API 문서대로 importer만 설정하고, 에러가 발생하면 추가 조사 필요
            if (isForeignOrigin) {
                // 수입사: 필수 필드 (API 문서 명시)
                // 기본값 설정 (추후 엑셀/설정값으로 교체 가능)
                originAreaInfo.put("importer", NaverApiConstants.DEFAULT_IMPORTER); // TODO: 엑셀/설정/Store에서 가져오도록 확장
                
                // TODO: 에러 메시지 "수입국 항목을 입력해 주세요"가 계속 발생하면
                // 원산지 코드 조회 API(/v1/product-origin-areas)를 호출하여
                // 수입국 코드 정보를 확인하고 적절한 필드명/값을 사용해야 함
                // 현재는 API 문서에 없는 importCountry 필드를 추가하지 않음
                
                log.info("해외 원산지 감지: originArea={}, originAreaCode={}, importer={}", 
                        trimmedOriginArea, originAreaCode, originAreaInfo.get("importer"));
            }
            
            // content 필드는 항상 설정됨 (원산지 표시 내용)
            // API 문서: originAreaCode가 '04'(기타-직접 입력)인 경우 content 필수
            // 하지만 다른 코드에서도 content는 유용하므로 항상 설정
            
            // 해산물이 아닌 카테고리에서는 해역명 관련 필드를 명시적으로 제거
            // (에러: OceanTypeNotFullySelected, NotMarineProduct 방지)
            // 해외 원산지인 경우에도 해역명 필드가 포함되어 있으면 안 됨
            if (!isMarineCategory) {
                // 해역명 관련 필드가 있다면 제거 (혹시 모를 경우 대비)
                originAreaInfo.remove("oceanName");
                originAreaInfo.remove("oceanType");
                // 해역명 관련 다른 필드도 제거 (안전을 위해)
                originAreaInfo.remove("oceanArea");
                log.debug("해산물이 아닌 카테고리: oceanName/oceanType/oceanArea 제거됨, isForeignOrigin={}", isForeignOrigin);
            } else {
                // 해산물인 경우에만 해역명 정보가 필요하지만, 
                // 현재는 해역명 정보를 제공하지 않으므로 해산물 카테고리도 일반 원산지 정보만 사용
                // 필요시 해역명 정보를 추가할 수 있도록 주석 처리
                log.debug("해산물 카테고리이지만 해역명 정보는 제공하지 않습니다.");
            }
        } else {
            // 기본값: 국산
            originAreaInfo.put("originAreaCode", NaverApiConstants.OriginAreaCode.DOMESTIC); // API 문서: 00=국산
            originAreaInfo.put("content", NaverApiConstants.DEFAULT_ORIGIN_AREA);
        }
        
        // 최종 확인: originAreaCode가 반드시 포함되어 있어야 함
        if (!originAreaInfo.containsKey("originAreaCode")) {
            log.error("createOriginAreaInfo 반환 시 originAreaCode가 없습니다! originAreaInfo={}", originAreaInfo);
            // 기본값 설정: 국산
            originAreaInfo.put("originAreaCode", NaverApiConstants.OriginAreaCode.DOMESTIC);
        }
        
        log.debug("createOriginAreaInfo 반환: originAreaInfo={}", originAreaInfo);
        return originAreaInfo;
    }
    
    /**
     * 원산지 문자열로부터 원산지 코드를 추론합니다.
     * 
     * 네이버 API 문서에 따른 originAreaCode 값:
     * - "00": 국산
     * - "01": 원양산
     * - "02": 수입산
     * - "03": 기타-상세 설명에 표시
     * - "04": 기타-직접 입력 (content 필수)
     * - "05": 원산지 표기 의무 대상 아님
     * 
     * @param originArea 원산지 문자열 (예: "국내산", "중국", "일본" 등)
     * @return 원산지 코드 (00=국산, 02=수입산)
     */
    public String determineOriginAreaCode(String originArea) {
        if (originArea == null || originArea.trim().isEmpty()) {
            return NaverApiConstants.OriginAreaCode.DOMESTIC; // 기본값: 국산
        }
        
        String trimmed = originArea.trim();
        
        // 국내산 판단 (명시적으로 국내산인 경우만)
        if (trimmed.contains("국내") || trimmed.contains("한국") || trimmed.contains("국산")) {
            return NaverApiConstants.OriginAreaCode.DOMESTIC; // 국산
        }
        
        // 이하는 전부 해외산(수입산) 취급
        // API 문서에 따르면 수입산은 "02" 사용
        log.debug("해외 원산지로 판단: {}. originAreaCode=02(수입산) 사용", trimmed);
        return NaverApiConstants.OriginAreaCode.IMPORT_START; // 수입산
    }
}

