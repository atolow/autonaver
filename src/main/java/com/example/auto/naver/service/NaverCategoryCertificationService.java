package com.example.auto.naver.service;

import com.example.auto.naver.constants.NaverApiConstants;
import com.example.auto.naver.util.NaverCategoryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 네이버 카테고리별 인증 정보 처리 서비스
 * KC 인증, 어린이제품 인증 등 카테고리별 특수 요구사항 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverCategoryCertificationService {
    
    /**
     * 카테고리별 인증 정보 생성
     * 
     * @param categoryPath 카테고리 경로
     * @param leafCategoryId 카테고리 ID
     * @return 인증 관련 정보 Map (productCertificationInfos, certificationTargetExcludeContent)
     */
    public Map<String, Object> createCertificationInfo(String categoryPath, String leafCategoryId) {
        Map<String, Object> result = new HashMap<>();
        
        // KC 인증이 필요한 카테고리인지 확인
        boolean isKcCertificationRequired = NaverCategoryValidator.isKcCertificationRequired(categoryPath, leafCategoryId);
        boolean isChildCertificationRequired = NaverCategoryValidator.isChildCertificationRequired(categoryPath, leafCategoryId);
        
        // productCertificationInfos 배열 생성
        List<Map<String, Object>> productCertificationInfos = new ArrayList<>();
        
        // KC 인증 대상 카테고리 처리
        // API 문서에 따르면:
        // - productCertificationInfos 배열의 각 항목에 certificationInfoId (integer<int64>) required
        // - certificationInfoId를 모를 경우, productCertificationInfos를 비우고 kcCertifiedProductExclusionYn을 TRUE로 설정하여 인증 대상에서 제외
        // - certificationTargetExcludeContent의 kcCertifiedProductExclusionYn이 'KC 인증 대상' 카테고리 상품인 경우 필수
        //   가능한 값: FALSE(KC 인증 대상), TRUE(KC 인증 대상 아님), KC_EXEMPTION_OBJECT(안전기준준수, 구매대행, 병행수입)
        if (isKcCertificationRequired) {
            // certificationInfoId를 모르는 경우, productCertificationInfos를 비우고 인증 대상에서 제외
            // 실제 KC 인증 정보가 있는 경우에만 productCertificationInfos에 추가해야 함
            // 현재는 certificationInfoId를 모르므로 인증 대상에서 제외 처리
            log.warn("KC 인증 대상 카테고리 감지: 카테고리={}, certificationInfoId를 모르므로 인증 대상에서 제외 처리", 
                    categoryPath != null ? categoryPath : leafCategoryId);
            log.warn("실제 KC 인증 정보(certificationInfoId, certificationNumber 등)가 있으면 엑셀에 추가하거나 설정값으로 변경하세요.");
        }
        
        result.put("productCertificationInfos", productCertificationInfos);
        
        // certificationTargetExcludeContent 추가
        // KC 인증 대상 카테고리인 경우 필수이지만, 감지되지 않은 경우를 대비하여 모든 카테고리에 추가
        // API 문서: 'KC 인증 대상' 카테고리 상품인 경우 kcCertifiedProductExclusionYn 필수
        // API 문서: '어린이제품 인증 대상' 카테고리 상품인 경우 childCertifiedProductExclusionYn 필수
        // KC 인증 대상이 아닌 카테고리에서도 추가해도 문제없음 (안전장치)
        Map<String, Object> certificationTargetExcludeContent = new HashMap<>();
        
        // KC 인증 대상 카테고리 처리
        if (isKcCertificationRequired) {
            // KC 인증 대상 카테고리인 경우, certificationInfoId를 모르므로 인증 대상에서 제외 처리
            // kcCertifiedProductExclusionYn: FALSE(KC 인증 대상), TRUE(KC 인증 대상 아님), KC_EXEMPTION_OBJECT(안전기준준수, 구매대행, 병행수입)
            certificationTargetExcludeContent.put("kcCertifiedProductExclusionYn", NaverApiConstants.KcCertifiedProductExclusion.TRUE);
            log.info("certificationTargetExcludeContent 추가: kcCertifiedProductExclusionYn=TRUE (KC 인증 대상에서 제외)");
        } else {
            // KC 인증 대상이 아닌 것으로 판단되었지만, 안전을 위해 추가
            // 일부 카테고리는 감지되지 않을 수 있으므로 기본값으로 TRUE 설정
            certificationTargetExcludeContent.put("kcCertifiedProductExclusionYn", NaverApiConstants.KcCertifiedProductExclusion.TRUE);
            log.debug("certificationTargetExcludeContent 추가 (안전장치): kcCertifiedProductExclusionYn=TRUE");
        }
        
        // 어린이제품 인증 대상 카테고리 처리
        if (isChildCertificationRequired) {
            // 어린이제품 인증 대상 카테고리인 경우, certificationInfoId를 모르므로 인증 대상에서 제외 처리
            // childCertifiedProductExclusionYn: false(어린이제품 인증 대상), true(어린이제품 인증 대상 아님)
            // 미입력 시 false로 저장되므로, 인증 대상에서 제외하려면 true로 설정
            certificationTargetExcludeContent.put("childCertifiedProductExclusionYn", NaverApiConstants.DEFAULT_CHILD_CERTIFIED_PRODUCT_EXCLUSION);
            log.info("certificationTargetExcludeContent 추가: childCertifiedProductExclusionYn=true (어린이제품 인증 대상에서 제외)");
        } else {
            // 어린이제품 인증 대상이 아닌 것으로 판단되었지만, 안전을 위해 추가
            // 일부 카테고리는 감지되지 않을 수 있으므로 기본값으로 true 설정
            certificationTargetExcludeContent.put("childCertifiedProductExclusionYn", NaverApiConstants.DEFAULT_CHILD_CERTIFIED_PRODUCT_EXCLUSION);
            log.debug("certificationTargetExcludeContent 추가 (안전장치): childCertifiedProductExclusionYn=true");
        }
        
        result.put("certificationTargetExcludeContent", certificationTargetExcludeContent);
        
        return result;
    }
}

