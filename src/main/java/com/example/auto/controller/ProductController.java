package com.example.auto.controller;

import com.example.auto.dto.GroupProductRequest;
import com.example.auto.dto.ProductRequest;
import com.example.auto.dto.ProductSearchRequest;
import com.example.auto.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 상품 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    
    /**
     * 상품 목록 조회
     * 검색 조건에 따라 상품 목록을 조회합니다.
     * (독립 실행형: 현재 등록된 스토어에 자동으로 조회)
     * 
     * @param request 검색 요청 데이터
     * @return 상품 목록
     */
    @PostMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestBody ProductSearchRequest request) {
        try {
            log.info("상품 목록 조회 요청: searchKeywordType={}, page={}, size={}", 
                    request.getSearchKeywordType(), request.getPage(), request.getSize());
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            // 기본값 설정
            if (request.getPage() == null || request.getPage() < 1) {
                request.setPage(1);
            }
            if (request.getSize() == null || request.getSize() < 1) {
                request.setSize(50);
            }
            if (request.getSize() > 500) {
                request.setSize(500); // 최대 500건
            }
            
            Map<String, Object> result = productService.searchProducts(currentStore.getId(), request);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("상품 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("상품 목록 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "상품 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 네이버 스마트스토어에 상품 등록
     * (독립 실행형: 현재 등록된 스토어에 자동으로 등록)
     * 
     * @param request 상품 등록 요청 데이터
     * @return 등록된 상품 정보
     */
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request) {
        try {
            log.info("일반 상품 등록 API 호출됨: name={}, originProduct={}", 
                    request.getName(), request.getOriginProduct() != null);
            
            // 그룹상품 등록 요청인지 확인 (groupProduct 필드가 있으면)
            // Jackson이 @JsonIgnoreProperties로 인해 무시했을 수 있으므로, 
            // 요청 본문을 직접 확인하는 것은 어렵지만, 
            // name과 originProduct가 모두 null이고 다른 필드도 없으면 그룹상품 등록일 가능성이 높음
            if (request.getName() == null && request.getOriginProduct() == null && 
                request.getLeafCategoryId() == null && request.getSalePrice() == null) {
                log.warn("일반 상품 등록 API에 그룹상품 등록 요청이 들어온 것으로 보입니다. /api/products/group 엔드포인트를 사용하세요.");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "그룹상품 등록은 POST /api/products/group 엔드포인트를 사용해야 합니다.", 
                                "code", "WRONG_ENDPOINT",
                                "hint", "URL을 /api/products/group로 변경하고 groupProduct 객체를 포함한 JSON을 보내세요."));
            }
            
            // 필수 필드 검증
            validateProductRequest(request);
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.createProduct(currentStore.getId(), request);
            return ResponseEntity.ok(result);
            
        } catch (org.springframework.http.converter.HttpMessageNotReadableException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "JSON 형식이 올바르지 않습니다: " + e.getMessage(), 
                            "code", "JSON_PARSE_ERROR",
                            "hint", "네이버 API 형식 또는 간단한 형식으로 요청하세요."));
            
        } catch (IllegalArgumentException e) {
            log.error("일반 상품 등록 실패: {}", e.getMessage());
            log.error("요청 데이터: name={}, originProduct={}, leafCategoryId={}", 
                    request.getName(), 
                    request.getOriginProduct() != null,
                    request.getLeafCategoryId());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("상품 등록 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "상품 등록 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 상품 등록 요청 데이터 검증
     * 네이버 API 형식(originProduct)과 간단한 형식 모두 지원
     */
    private void validateProductRequest(ProductRequest request) {
        // 네이버 API 형식인 경우
        if (request.getOriginProduct() != null) {
            ProductRequest.OriginProduct originProduct = request.getOriginProduct();
            
            if (originProduct.getName() == null || originProduct.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("상품명(name)은 필수입니다.");
            }
            if (originProduct.getLeafCategoryId() == null || originProduct.getLeafCategoryId().trim().isEmpty()) {
                throw new IllegalArgumentException("카테고리 ID(leafCategoryId)는 필수입니다.");
            }
            if (originProduct.getDetailContent() == null || originProduct.getDetailContent().trim().isEmpty()) {
                throw new IllegalArgumentException("상품 상세 정보(detailContent)는 필수입니다.");
            }
            if (originProduct.getSalePrice() == null || originProduct.getSalePrice() <= 0) {
                throw new IllegalArgumentException("판매 가격(salePrice)은 필수이며 0보다 커야 합니다.");
            }
            if (originProduct.getStockQuantity() == null || originProduct.getStockQuantity() < 0) {
                throw new IllegalArgumentException("재고 수량(stockQuantity)은 필수이며 0 이상이어야 합니다.");
            }
            if (originProduct.getImages() == null || originProduct.getImages().isEmpty()) {
                throw new IllegalArgumentException("상품 이미지(images)는 필수입니다.");
            }
            if (originProduct.getDetailAttribute() == null || originProduct.getDetailAttribute().isEmpty()) {
                throw new IllegalArgumentException("상품 상세 속성(detailAttribute)은 필수입니다.");
            }
            
            // smartstoreChannelProduct 검증
            if (request.getSmartstoreChannelProduct() != null) {
                ProductRequest.SmartstoreChannelProduct scp = request.getSmartstoreChannelProduct();
                if (scp.getNaverShoppingRegistration() == null) {
                    throw new IllegalArgumentException("네이버 쇼핑 등록 여부(naverShoppingRegistration)는 필수입니다.");
                }
                if (scp.getChannelProductDisplayStatusType() == null || scp.getChannelProductDisplayStatusType().trim().isEmpty()) {
                    throw new IllegalArgumentException("전시 상태(channelProductDisplayStatusType)는 필수입니다.");
                }
            }
            
        } else {
            // 간단한 형식인 경우
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("상품명은 필수입니다.");
            }
            if (request.getLeafCategoryId() == null || request.getLeafCategoryId().trim().isEmpty()) {
                throw new IllegalArgumentException("카테고리 ID는 필수입니다.");
            }
            if (request.getDetailContent() == null || request.getDetailContent().trim().isEmpty()) {
                throw new IllegalArgumentException("상품 상세 정보는 필수입니다.");
            }
            if (request.getSalePrice() == null || request.getSalePrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("판매 가격은 필수이며 0보다 커야 합니다.");
            }
            if (request.getStockQuantity() == null || request.getStockQuantity() < 0) {
                throw new IllegalArgumentException("재고 수량은 필수이며 0 이상이어야 합니다.");
            }
            if (request.getImages() == null || request.getImages().isEmpty()) {
                throw new IllegalArgumentException("상품 이미지는 최소 1개 이상 필요합니다.");
            }
        }
    }
    
    /**
     * 네이버 스마트스토어에 그룹상품 등록
     * 여러 개의 상품을 하나의 그룹으로 묶어 등록
     * (독립 실행형: 현재 등록된 스토어에 자동으로 등록)
     * 
     * @param request 그룹상품 등록 요청 데이터
     * @return 등록된 그룹상품 정보
     */
    @PostMapping("/group")
    public ResponseEntity<?> createGroupProduct(@RequestBody GroupProductRequest request) {
        try {
            // 요청 데이터 로깅 (디버깅용)
            log.info("그룹상품 등록 요청 수신: groupProduct={}, groupName={}, leafCategoryId={}, guideId={}, specificProducts={}", 
                    request.getGroupProduct() != null,
                    request.getGroupName(),
                    request.getLeafCategoryId(),
                    request.getGuideId(),
                    request.getSpecificProducts() != null ? request.getSpecificProducts().size() : 0);
            
            // 필수 필드 검증
            validateGroupProductRequest(request);
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.createGroupProduct(currentStore.getId(), request);
            return ResponseEntity.ok(result);
            
        } catch (org.springframework.http.converter.HttpMessageNotReadableException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "JSON 형식이 올바르지 않습니다: " + e.getMessage(), 
                            "code", "JSON_PARSE_ERROR",
                            "hint", "originProducts 배열 또는 products 배열을 포함하여 요청하세요."));
            
        } catch (IllegalArgumentException e) {
            log.error("그룹상품 등록 실패: {}", e.getMessage());
            log.error("요청 데이터: groupProduct={}, groupName={}, leafCategoryId={}, guideId={}", 
                    request.getGroupProduct() != null,
                    request.getGroupName(),
                    request.getLeafCategoryId(),
                    request.getGuideId());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("그룹상품 등록 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "그룹상품 등록 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 그룹상품 요청 결과 조회
     * 그룹상품 등록/수정 API의 처리 상태를 조회합니다.
     * 
     * @param type 요청 타입 (CREATE: 그룹상품 등록, UPDATE: 그룹상품 수정)
     * @param requestId 조회할 요청 ID
     * @return 그룹상품 요청 결과
     */
    @GetMapping("/group/status")
    public ResponseEntity<?> getGroupProductStatus(
            @RequestParam String type,
            @RequestParam String requestId) {
        try {
            log.info("그룹상품 요청 결과 조회 요청: type={}, requestId={}", type, requestId);
            
            // type 검증
            if (!"CREATE".equals(type) && !"UPDATE".equals(type)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "type은 CREATE 또는 UPDATE여야 합니다.", 
                                "code", "INVALID_TYPE"));
            }
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.getGroupProductStatus(
                    currentStore.getId(), type, requestId);
            
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("그룹상품 요청 결과 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("그룹상품 요청 결과 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "그룹상품 요청 결과 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 그룹상품 등록 요청 데이터 검증
     * 실제 API 구조: { "groupProduct": { "leafCategoryId", "name", "guideId", "specificProducts": [...] } }
     */
    private void validateGroupProductRequest(GroupProductRequest request) {
        log.info("그룹상품 등록 요청 검증 시작: groupProduct={}, leafCategoryId={}, guideId={}, groupName={}, specificProducts={}", 
                request.getGroupProduct() != null,
                request.getLeafCategoryId(),
                request.getGuideId(),
                request.getGroupName(),
                request.getSpecificProducts() != null ? request.getSpecificProducts().size() : 0);
        
        // 요청 데이터가 비어있는지 확인
        if (request.getGroupProduct() == null && 
            (request.getLeafCategoryId() == null || request.getGuideId() == null || 
             request.getSpecificProducts() == null || request.getSpecificProducts().isEmpty())) {
            throw new IllegalArgumentException("그룹상품 등록을 위해서는 groupProduct 객체 또는 필수 필드(leafCategoryId, guideId, specificProducts)가 필요합니다.");
        }
        
        // 네이버 API 형식인 경우 (groupProduct 객체)
        if (request.getGroupProduct() != null) {
            GroupProductRequest.GroupProduct gp = request.getGroupProduct();
            
            // 필수 필드 검증
            if (gp.getLeafCategoryId() == null || gp.getLeafCategoryId().trim().isEmpty()) {
                throw new IllegalArgumentException("카테고리 ID(leafCategoryId)는 필수입니다.");
            }
            if (gp.getName() == null || gp.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("그룹상품명(groupProduct.name)은 필수입니다.");
            }
            if (gp.getGuideId() == null) {
                throw new IllegalArgumentException("판매 옵션 가이드 ID(guideId)는 필수입니다.");
            }
            if (gp.getMinorPurchasable() == null) {
                throw new IllegalArgumentException("미성년자 구매 가능 여부(minorPurchasable)는 필수입니다.");
            }
            if (gp.getProductInfoProvidedNotice() == null || gp.getProductInfoProvidedNotice().isEmpty()) {
                throw new IllegalArgumentException("상품정보제공고시(productInfoProvidedNotice)는 필수입니다.");
            }
            if (gp.getAfterServiceInfo() == null || gp.getAfterServiceInfo().isEmpty()) {
                throw new IllegalArgumentException("A/S 정보(afterServiceInfo)는 필수입니다.");
            }
            if (gp.getSpecificProducts() == null || gp.getSpecificProducts().isEmpty()) {
                throw new IllegalArgumentException("개별 상품(specificProducts) 배열은 필수이며 최소 1개 이상 필요합니다.");
            }
            
            // specificProducts 검증
            for (int i = 0; i < gp.getSpecificProducts().size(); i++) {
                GroupProductRequest.SpecificProduct sp = gp.getSpecificProducts().get(i);
                
                if (sp.getSaleOptions() == null || sp.getSaleOptions().isEmpty()) {
                    throw new IllegalArgumentException("판매 옵션(saleOptions)은 필수입니다. (상품 " + (i + 1) + "번)");
                }
                if (sp.getSalePrice() == null || sp.getSalePrice() <= 0) {
                    throw new IllegalArgumentException("판매 가격(salePrice)은 필수이며 0보다 커야 합니다. (상품 " + (i + 1) + "번)");
                }
                if (sp.getStockQuantity() == null || sp.getStockQuantity() < 0) {
                    throw new IllegalArgumentException("재고 수량(stockQuantity)은 필수이며 0 이상이어야 합니다. (상품 " + (i + 1) + "번)");
                }
                if ((sp.getImages() == null || sp.getImages().isEmpty()) && 
                    (sp.getImageUrls() == null || sp.getImageUrls().isEmpty())) {
                    throw new IllegalArgumentException("상품 이미지(images 또는 imageUrls)는 필수입니다. (상품 " + (i + 1) + "번)");
                }
            }
            
        } else if (request.getLeafCategoryId() != null && request.getGuideId() != null && 
                   request.getSpecificProducts() != null && !request.getSpecificProducts().isEmpty()) {
            // 사용자 친화적 형식인 경우
            if (request.getLeafCategoryId().trim().isEmpty()) {
                throw new IllegalArgumentException("카테고리 ID(leafCategoryId)는 필수입니다.");
            }
            if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
                throw new IllegalArgumentException("그룹상품명(groupName)은 필수입니다. (사용자 친화적 형식 사용 시)");
            }
            
            // specificProducts 검증
            for (int i = 0; i < request.getSpecificProducts().size(); i++) {
                GroupProductRequest.SpecificProduct sp = request.getSpecificProducts().get(i);
                
                if (sp.getSaleOptions() == null || sp.getSaleOptions().isEmpty()) {
                    throw new IllegalArgumentException("판매 옵션(saleOptions)은 필수입니다. (상품 " + (i + 1) + "번)");
                }
                if (sp.getSalePrice() == null || sp.getSalePrice() <= 0) {
                    throw new IllegalArgumentException("판매 가격(salePrice)은 필수이며 0보다 커야 합니다. (상품 " + (i + 1) + "번)");
                }
                if (sp.getStockQuantity() == null || sp.getStockQuantity() < 0) {
                    throw new IllegalArgumentException("재고 수량(stockQuantity)은 필수이며 0 이상이어야 합니다. (상품 " + (i + 1) + "번)");
                }
                if ((sp.getImages() == null || sp.getImages().isEmpty()) && 
                    (sp.getImageUrls() == null || sp.getImageUrls().isEmpty())) {
                    throw new IllegalArgumentException("상품 이미지(images 또는 imageUrls)는 필수입니다. (상품 " + (i + 1) + "번)");
                }
            }
        } else {
            throw new IllegalArgumentException("그룹상품 등록을 위해서는 groupProduct 객체 또는 필수 필드(leafCategoryId, guideId, specificProducts)가 필요합니다.");
        }
    }
    
    /**
     * 브랜드 조회
     * 전체 브랜드 목록을 조회합니다.
     * (독립 실행형: 현재 등록된 스토어에 자동으로 조회)
     * 
     * 참고: 네이버 API의 브랜드 조회 API가 현재 사용 불가능할 수 있습니다.
     * 네이버 스마트스토어 관리자 페이지에서 브랜드를 직접 등록해야 할 수 있습니다.
     * 
     * @return 브랜드 목록
     */
    @GetMapping("/brands")
    public ResponseEntity<?> getProductBrands() {
        try {
            log.info("브랜드 조회 요청");
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.getProductBrands(currentStore.getId());
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("브랜드 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // 네이버 API 에러 처리
            String errorMessage = "네이버 API 브랜드 조회 실패";
            if (e.getStatusCode().value() == 400) {
                errorMessage = "브랜드 조회 API가 현재 사용 불가능합니다. 네이버 스마트스토어 관리자 페이지에서 브랜드를 직접 등록해야 할 수 있습니다.";
            } else if (e.getStatusCode().value() == 401) {
                errorMessage = "인증 실패. Access Token을 확인해주세요.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "브랜드 조회 권한이 없습니다.";
            }
            
            log.error("브랜드 조회 중 네이버 API 오류 발생: status={}, message={}", 
                    e.getStatusCode(), e.getMessage());
            
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of(
                            "error", errorMessage,
                            "code", "NAVER_API_ERROR",
                            "status", e.getStatusCode().value(),
                            "detail", e.getMessage() != null ? e.getMessage() : "네이버 API 에러"
                    ));
                    
        } catch (Exception e) {
            log.error("브랜드 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "브랜드 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 전체 사이즈 타입 조회
     * 전체 사이즈 타입 목록을 조회합니다.
     * (독립 실행형: 현재 등록된 스토어에 자동으로 조회)
     * 
     * @return 사이즈 타입 목록
     */
    @GetMapping("/sizes")
    public ResponseEntity<?> getProductSizes() {
        try {
            log.info("사이즈 타입 조회 요청");
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.getProductSizes(currentStore.getId());
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("사이즈 타입 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("사이즈 타입 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "사이즈 타입 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 원상품 조회
     * 원상품 번호로 원상품 정보를 조회합니다.
     * (독립 실행형: 현재 등록된 스토어에 자동으로 조회)
     * 
     * @param originProductNo 원상품 번호 (Path Variable)
     * @return 원상품 정보
     */
    @GetMapping("/origin-products/{originProductNo}")
    public ResponseEntity<?> getOriginProduct(@PathVariable Long originProductNo) {
        try {
            log.info("원상품 조회 요청: originProductNo={}", originProductNo);
            
            if (originProductNo == null || originProductNo <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "원상품 번호(originProductNo)는 필수이며 0보다 커야 합니다.", 
                                "code", "VALIDATION_ERROR"));
            }
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.getOriginProduct(currentStore.getId(), originProductNo);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("원상품 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("원상품 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "원상품 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
    
    /**
     * 판매 옵션 정보 조회
     * 그룹상품 등록 시 필요한 guideId를 조회하기 위해 사용
     * (독립 실행형: 현재 등록된 스토어에 자동으로 조회)
     * 
     * @param categoryId 리프 카테고리 ID (Query Parameter)
     * @return 판매 옵션 정보 (useOptionYn, optionGuides 배열 포함)
     */
    @GetMapping("/purchase-option-guides")
    public ResponseEntity<?> getPurchaseOptionGuides(@RequestParam String categoryId) {
        try {
            if (categoryId == null || categoryId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "카테고리 ID(categoryId)는 필수입니다.", "code", "VALIDATION_ERROR"));
            }
            
            // 현재 스토어 조회
            com.example.auto.domain.Store currentStore = productService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            
            Map<String, Object> result = productService.getPurchaseOptionGuides(currentStore.getId(), categoryId.trim());
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.error("판매 옵션 정보 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "VALIDATION_ERROR"));
                    
        } catch (Exception e) {
            log.error("판매 옵션 정보 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "판매 옵션 정보 조회 중 오류가 발생했습니다: " + e.getMessage(), 
                            "code", "INTERNAL_ERROR"));
        }
    }
}

