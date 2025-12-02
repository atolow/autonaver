package com.example.auto.controller;

import com.example.auto.domain.Store;
import com.example.auto.dto.AuthenticateRequest;
import com.example.auto.service.OAuthService;
import com.example.auto.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 인증 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthController {
    
    private final OAuthService oAuthService;
    
    private final StoreService storeService;
    
    /**
     * 네이버 커머스 API 인증 토큰 발급 및 스토어 등록
     * 전자서명 기반 인증 방식
     * 
     * 스토어가 이미 등록되어 있으면 저장된 정보를 자동으로 사용합니다.
     * 스토어가 없으면 최초 1회만 입력받습니다.
     * 
     * JSON Body, Form Data, Query Parameters 모두 지원합니다.
     * JSON Body가 있으면 우선 사용하고, 없으면 Form Data 또는 Query Parameters를 사용합니다.
     * 
     * @param request JSON Body (선택)
     * @param type 인증 토큰 발급 타입 (Query Parameter 또는 Form Data, 선택)
     * @param accountId 판매자 ID (Query Parameter 또는 Form Data, 선택)
     * @param vendorId 판매자 ID (Query Parameter 또는 Form Data, 선택)
     * @param storeName 스토어 이름 (Query Parameter 또는 Form Data, 선택)
     * @return 스토어 정보
     */
    @PostMapping(value = "/authenticate")
    public ResponseEntity<?> authenticate(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String vendorId,
            @RequestParam(required = false) String storeName) {
        
        // Form Data 또는 Query Parameters 사용
        String finalType = type;
        String finalAccountId = accountId;
        String finalVendorId = vendorId;
        String finalStoreName = storeName;
        
        try {
            // 스토어가 이미 등록되어 있는지 확인
            java.util.Optional<Store> existingStore = storeService.getCurrentStore();
            
            if (existingStore.isPresent()) {
                // 스토어가 있으면 저장된 정보로 자동 토큰 갱신
                Store store = existingStore.get();
                log.info("기존 스토어 발견, 자동 토큰 갱신: vendorId={}", store.getVendorId());
                
                // 저장된 vendorId를 accountId로 사용 (기본값)
                String autoAccountId = store.getVendorId();
                String autoType = finalType != null ? finalType : "SELF"; // 기본값 SELF
                
                Store updatedStore = oAuthService.authenticateAndCreateStore(
                        autoType, 
                        autoAccountId, 
                        store.getVendorId(), 
                        store.getStoreName()
                );
                
                return ResponseEntity.ok(updatedStore);
            } else {
                // 스토어가 없으면 최초 설정 (필수 정보 입력 필요)
                String finalTypeValue = finalType != null ? finalType : "SELF"; // 기본값 SELF
                
                if ("SELF".equals(finalTypeValue) && (finalAccountId == null || finalAccountId.isEmpty())) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "최초 설정: type이 SELF인 경우 accountId는 필수입니다.");
                    error.put("message", "스토어가 등록되지 않았습니다. accountId를 입력하세요.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
                }
                
                Store store = oAuthService.authenticateAndCreateStore(finalTypeValue, finalAccountId, finalVendorId, finalStoreName);
                return ResponseEntity.ok(store);
            }
            
        } catch (Exception e) {
            log.error("네이버 커머스 API 인증 처리 중 오류 발생", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 네이버 커머스 API 인증 토큰 발급 및 스토어 등록 (JSON Body 지원)
     * 전자서명 기반 인증 방식
     * 
     * JSON Body로 요청하는 경우 사용합니다.
     * 
     * @param request 인증 요청 데이터 (JSON Body)
     * @return 스토어 정보
     */
    @PostMapping(value = "/authenticate/json", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> authenticateJson(@RequestBody AuthenticateRequest request) {
        return authenticate(
                request.getType(),
                request.getAccountId(),
                request.getVendorId(),
                request.getStoreName()
        );
    }
}

