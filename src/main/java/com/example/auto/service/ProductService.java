package com.example.auto.service;

import com.example.auto.client.NaverCommerceClient;
import com.example.auto.domain.Store;
import com.example.auto.dto.GroupProductRequest;
import com.example.auto.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    
    private final NaverCommerceClient naverCommerceClient;
    private final StoreService storeService;
    
    // 원산지 코드 정보 캐시 (한 번 조회 후 재사용)
    private Map<String, Object> originAreaCodesCache = null;
    
    // 원산지 이름 -> 코드 매핑 캐시 (빠른 조회용)
    private Map<String, String> originNameToCodeMap = null;
    
    /**
     * 현재 등록된 스토어 조회 (독립 실행형용)
     */
    public java.util.Optional<com.example.auto.domain.Store> getCurrentStore() {
        return storeService.getCurrentStore();
    }
    
    /**
     * 원산지 코드 정보 조회 (캐시 사용)
     * 
     * @param accessToken Access Token
     * @return 원산지 코드 정보 Map
     */
    private Map<String, Object> getOriginAreaCodesCached(String accessToken) {
        if (originAreaCodesCache == null) {
            try {
                log.info("원산지 코드 정보 조회 시작...");
                Map<String, Object> response = naverCommerceClient.getOriginAreaCodes(accessToken).block();
                
                if (response != null) {
                    originAreaCodesCache = response;
                    log.info("원산지 코드 정보 조회 성공: {}", response);
                    
                    // originAreaCodeNames 배열 확인 및 매핑 생성
                    if (response.containsKey("originAreaCodeNames")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> codes = (List<Map<String, Object>>) response.get("originAreaCodeNames");
                        if (codes != null && !codes.isEmpty()) {
                            log.info("원산지 코드 개수: {}", codes.size());
                            
                            // 원산지 이름 -> 코드 매핑 생성
                            originNameToCodeMap = new HashMap<>();
                            for (Map<String, Object> codeInfo : codes) {
                                String code = (String) codeInfo.get("code");
                                String name = (String) codeInfo.get("name");
                                
                                if (code != null && name != null) {
                                    // 국가명 추출 (예: "수입산:아시아>베트남" -> "베트남")
                                    String countryName = extractCountryName(name);
                                    if (countryName != null && !countryName.isEmpty()) {
                                        originNameToCodeMap.put(countryName.toLowerCase(), code);
                                    }
                                    
                                    // 전체 이름도 매핑 (예: "베트남" -> "0200014")
                                    originNameToCodeMap.put(name.toLowerCase(), code);
                                }
                            }
                            
                            log.info("원산지 이름->코드 매핑 생성 완료: {}개", originNameToCodeMap.size());
                            log.info("원산지 코드 예시 (처음 5개):");
                            for (int i = 0; i < Math.min(5, codes.size()); i++) {
                                Map<String, Object> codeInfo = codes.get(i);
                                log.info("  - code: {}, name: {}", codeInfo.get("code"), codeInfo.get("name"));
                            }
                        }
                    } else {
                        log.warn("원산지 코드 응답에 originAreaCodeNames가 없습니다. 전체 응답: {}", response);
                    }
                } else {
                    log.warn("원산지 코드 정보 조회 응답이 null입니다.");
                }
            } catch (Exception e) {
                log.error("원산지 코드 정보 조회 실패", e);
                // 에러가 발생해도 계속 진행 (기본값 사용)
            }
        }
        return originAreaCodesCache;
    }
    
    /**
     * 네이버 스마트스토어에 상품 등록
     * 
     * @param storeId 스토어 ID
     * @param request 상품 등록 요청 데이터
     * @return 등록된 상품 정보
     */
    public Map<String, Object> createProduct(Long storeId, ProductRequest request) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("상품 등록 시작: 스토어={}, 상품명={}", store.getStoreName(), request.getName());
        
        // 원산지 코드 정보 조회 (첫 호출 시에만, 캐시 사용)
        getOriginAreaCodesCached(store.getAccessToken());
        
        // 이미지가 있으면 먼저 네이버 서버에 업로드
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            log.info("이미지 업로드 시작: {}개 이미지", request.getImages().size());
            List<String> uploadedImageUrls = new ArrayList<>();
            
            for (int i = 0; i < request.getImages().size(); i++) {
                String imageUrl = request.getImages().get(i);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    // Rate Limit 방지를 위해 이미지 업로드 사이에 딜레이 추가 (첫 번째 이미지 제외)
                    if (i > 0) {
                        try {
                            Thread.sleep(300); // 300ms 딜레이 (Rate Limit 방지)
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("이미지 업로드 딜레이 중단됨");
                        }
                    }
                    
                    // 재시도 로직으로 이미지 업로드 시도
                    String uploadedUrl = uploadImageWithRetry(
                            store.getAccessToken(),
                            store.getVendorId(),
                            imageUrl.trim(),
                            3 // 최대 3회 재시도
                    );
                    
                    if (uploadedUrl != null && !uploadedUrl.isEmpty()) {
                        uploadedImageUrls.add(uploadedUrl);
                        log.info("이미지 업로드 성공: {} -> {}", imageUrl, uploadedUrl);
                    } else {
                        log.error("이미지 업로드 실패: {}", imageUrl);
                        throw new IllegalStateException("이미지 업로드 실패: " + imageUrl + ". 네이버 서버에 이미지를 업로드해야 합니다.");
                    }
                }
            }
            
            // 업로드된 이미지 URL로 교체
            request.setImages(uploadedImageUrls);
            log.info("이미지 업로드 완료: {}개 이미지", uploadedImageUrls.size());
        }
        
        // 사용자 친화적인 요청을 네이버 API 형식으로 변환
        Map<String, Object> naverProductData = convertToNaverFormat(request);
        
        // 디버깅: originAreaInfo 확인
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> originProduct = (Map<String, Object>) naverProductData.get("originProduct");
            if (originProduct != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> detailAttribute = (Map<String, Object>) originProduct.get("detailAttribute");
                if (detailAttribute != null) {
                    Object originAreaInfo = detailAttribute.get("originAreaInfo");
                    log.info("전송 전 originAreaInfo 확인: {}", originAreaInfo);
                }
            }
        } catch (Exception e) {
            log.debug("originAreaInfo 확인 중 오류", e);
        }
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.createProduct(
                store.getAccessToken(),
                store.getVendorId(),
                naverProductData
        ).block(); // 비동기를 동기로 변환
        
        log.info("상품 등록 완료: 스토어={}, 상품명={}", store.getStoreName(), request.getName());
        
        return result;
    }
    
    /**
     * 이미지 업로드 (Rate Limit 처리 및 재시도 로직 포함)
     * 
     * @param accessToken Access Token
     * @param vendorId 판매자 ID
     * @param imageUrl 이미지 URL
     * @param maxRetries 최대 재시도 횟수
     * @return 업로드된 이미지 URL
     */
    private String uploadImageWithRetry(String accessToken, String vendorId, String imageUrl, int maxRetries) {
        int retryCount = 0;
        long baseDelayMs = 1000; // 기본 딜레이 1초
        
        while (retryCount <= maxRetries) {
            try {
                // 네이버 이미지 업로드 API 호출
                Map<String, Object> uploadResponse = naverCommerceClient.uploadProductImage(
                        accessToken,
                        vendorId,
                        imageUrl
                ).block();
                
                // 업로드된 이미지 URL 추출
                String uploadedUrl = extractImageUrlFromResponse(uploadResponse);
                
                if (uploadedUrl != null && !uploadedUrl.isEmpty()) {
                    return uploadedUrl;
                } else {
                    log.error("이미지 업로드 응답에서 URL을 찾을 수 없습니다. 응답: {}", uploadResponse);
                    throw new IllegalStateException("이미지 업로드 응답에서 URL을 찾을 수 없습니다.");
                }
                
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests e) {
                // Rate Limit 에러 (429) 발생 시 재시도
                retryCount++;
                if (retryCount > maxRetries) {
                    log.error("이미지 업로드 실패 (Rate Limit): 최대 재시도 횟수 초과. imageUrl={}", imageUrl);
                    throw new IllegalStateException("이미지 업로드 실패 (Rate Limit): " + imageUrl + 
                            ". 네이버 API Rate Limit에 걸렸습니다. 잠시 후 다시 시도해주세요.", e);
                }
                
                // Exponential backoff: 1초, 2초, 4초...
                long delayMs = baseDelayMs * (1L << (retryCount - 1));
                log.warn("Rate Limit 에러 발생 (429). {}초 후 재시도 ({}/{})... imageUrl={}", 
                        delayMs / 1000, retryCount, maxRetries, imageUrl);
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("이미지 업로드 재시도 중단됨", ie);
                }
                
            } catch (Exception e) {
                // Rate Limit이 아닌 다른 에러는 즉시 실패 처리
                log.error("이미지 업로드 실패: {}", imageUrl, e);
                throw new IllegalStateException("이미지 업로드 실패: " + imageUrl + 
                        ". 네이버 서버에 이미지를 업로드해야 합니다.", e);
            }
        }
        
        throw new IllegalStateException("이미지 업로드 실패: " + imageUrl);
    }
    
    /**
     * 네이버 API 응답에서 이미지 URL 추출
     */
    @SuppressWarnings("unchecked")
    private String extractImageUrlFromResponse(Map<String, Object> uploadResponse) {
        if (uploadResponse == null) {
            return null;
        }
        
        // 네이버 API 응답 구조에 따라 URL 추출 시도
        // 가능한 구조:
        // 1. {"url": "..."}
        // 2. {"imageUrl": "..."}
        // 3. {"data": {"url": "..."}}
        // 4. {"images": [{"url": "..."}]}
        // 5. {"imageUrls": ["..."]}
        
        if (uploadResponse.containsKey("url")) {
            return (String) uploadResponse.get("url");
        } else if (uploadResponse.containsKey("imageUrl")) {
            return (String) uploadResponse.get("imageUrl");
        } else if (uploadResponse.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) uploadResponse.get("data");
            if (data != null) {
                if (data.containsKey("url")) {
                    return (String) data.get("url");
                } else if (data.containsKey("imageUrl")) {
                    return (String) data.get("imageUrl");
                } else if (data.containsKey("images") && data.get("images") instanceof List) {
                    List<Map<String, Object>> images = (List<Map<String, Object>>) data.get("images");
                    if (!images.isEmpty() && images.get(0).containsKey("url")) {
                        return (String) images.get(0).get("url");
                    }
                }
            }
        } else if (uploadResponse.containsKey("images") && uploadResponse.get("images") instanceof List) {
            List<Map<String, Object>> images = (List<Map<String, Object>>) uploadResponse.get("images");
            if (!images.isEmpty() && images.get(0).containsKey("url")) {
                return (String) images.get(0).get("url");
            }
        } else if (uploadResponse.containsKey("imageUrls") && uploadResponse.get("imageUrls") instanceof List) {
            List<String> imageUrls = (List<String>) uploadResponse.get("imageUrls");
            if (!imageUrls.isEmpty()) {
                return imageUrls.get(0);
            }
        }
        
        return null;
    }
    
    /**
     * 사용자 친화적인 요청을 네이버 API 형식으로 변환
     * 네이버 API 형식과 간단한 형식 모두 지원
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToNaverFormat(ProductRequest request) {
        Map<String, Object> naverData = new HashMap<>();
        
        // 네이버 API 형식으로 이미 들어온 경우 그대로 사용
        if (request.getOriginProduct() != null) {
            // request의 originArea가 있으면 그것을 우선 사용 (더 신뢰할 수 있음)
            String originAreaFromRequest = request.getOriginArea();
            Map<String, Object> originProductMap = convertOriginProductToMap(
                    request.getOriginProduct(), 
                    request.getCategoryPath(),
                    originAreaFromRequest);
            naverData.put("originProduct", originProductMap);
            
            // 스마트스토어 채널상품 정보
            if (request.getSmartstoreChannelProduct() != null) {
                Map<String, Object> channelProductMap = new HashMap<>();
                ProductRequest.SmartstoreChannelProduct scp = request.getSmartstoreChannelProduct();
                if (scp.getChannelProductName() != null) {
                    channelProductMap.put("channelProductName", scp.getChannelProductName());
                }
                if (scp.getBbsSeq() != null) {
                    channelProductMap.put("bbsSeq", scp.getBbsSeq());
                }
                if (scp.getStoreKeepExclusiveProduct() != null) {
                    channelProductMap.put("storeKeepExclusiveProduct", scp.getStoreKeepExclusiveProduct());
                }
                channelProductMap.put("naverShoppingRegistration", 
                        scp.getNaverShoppingRegistration() != null ? scp.getNaverShoppingRegistration() : false);
                channelProductMap.put("channelProductDisplayStatusType", 
                        scp.getChannelProductDisplayStatusType() != null ? scp.getChannelProductDisplayStatusType() : "ON");
                
                naverData.put("smartstoreChannelProduct", channelProductMap);
            } else {
                // 기본값
                Map<String, Object> channelProductMap = new HashMap<>();
                channelProductMap.put("naverShoppingRegistration", false);
                channelProductMap.put("channelProductDisplayStatusType", "ON");
                naverData.put("smartstoreChannelProduct", channelProductMap);
            }
            
            return naverData;
        }
        
        // 간단한 형식으로 들어온 경우 변환
        Map<String, Object> originProduct = new HashMap<>();
        
        // 필수 필드: statusType
        // 중요: 네이버 API 문서에 따르면 상품 등록 시에는 SALE(판매 중)만 입력할 수 있습니다.
        // OUTOFSTOCK(품절)은 재고가 0일 때 시스템이 자동으로 설정하는 상태입니다.
        // 따라서 상품 등록 시에는 항상 SALE로 설정합니다.
        String statusType = request.getStatusType();
        if (statusType != null && !statusType.trim().isEmpty()) {
            String upper = statusType.trim().toUpperCase();
            if (!"SALE".equals(upper)) {
                log.warn("상품 등록 시에는 statusType '{}'를 사용할 수 없습니다. SALE로 변환합니다. (재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정합니다.)", statusType);
                statusType = "SALE";
            }
        } else {
            statusType = "SALE";
        }
        originProduct.put("statusType", statusType);
        originProduct.put("saleType", request.getSaleType() != null ? request.getSaleType() : "NEW");
        originProduct.put("leafCategoryId", request.getLeafCategoryId());
        originProduct.put("name", request.getName());
        originProduct.put("detailContent", request.getDetailContent());
        originProduct.put("salePrice", request.getSalePrice().longValue());
        originProduct.put("stockQuantity", request.getStockQuantity());
        
        // 선택 필드: 브랜드
        if (request.getBrandName() != null && !request.getBrandName().trim().isEmpty()) {
            originProduct.put("brandName", request.getBrandName().trim());
        }
        
        // 선택 필드: 과세구분
        if (request.getTaxType() != null && !request.getTaxType().trim().isEmpty()) {
            originProduct.put("taxType", request.getTaxType().trim().toUpperCase());
        } else {
            originProduct.put("taxType", "TAX"); // 기본값: 과세
        }
        
        // 이미지 정보 (필수)
        Map<String, Object> images = new HashMap<>();
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<Map<String, String>> representImageList = new ArrayList<>();
            for (String imageUrl : request.getImages()) {
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    String trimmedUrl = imageUrl.trim();
                    
                    // URL 형식 검증
                    if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                        throw new IllegalArgumentException("이미지 URL은 http:// 또는 https://로 시작해야 합니다: " + trimmedUrl);
                    }
                    
                    // 이미지 파일 확장자 검증 (네이버가 지원하는 형식)
                    String lowerUrl = trimmedUrl.toLowerCase();
                    boolean isValidFormat = lowerUrl.endsWith(".jpg") || 
                                           lowerUrl.endsWith(".jpeg") || 
                                           lowerUrl.endsWith(".png") || 
                                           lowerUrl.endsWith(".gif") ||
                                           lowerUrl.contains(".jpg?") ||
                                           lowerUrl.contains(".jpeg?") ||
                                           lowerUrl.contains(".png?") ||
                                           lowerUrl.contains(".gif?");
                    
                    if (!isValidFormat) {
                        log.warn("이미지 URL이 지원되는 형식이 아닐 수 있습니다 (jpg, jpeg, png, gif): {}", trimmedUrl);
                        // 경고만 하고 계속 진행 (네이버 API가 최종 검증)
                    }
                    
                    // 네이버 API 이미지 형식: url 필드만 포함
                    Map<String, String> image = new HashMap<>();
                    image.put("url", trimmedUrl);
                    // 참고: 네이버 API는 외부 이미지 URL을 허용하지 않을 수 있습니다.
                    // 이미지를 먼저 네이버 서버에 업로드해야 할 수 있습니다.
                    representImageList.add(image);
                }
            }
            if (!representImageList.isEmpty()) {
                // 네이버 API 형식: representativeImage는 단일 객체, optionalImageList는 배열
                images.put("representativeImage", representImageList.get(0)); // 대표 이미지
                if (representImageList.size() > 1) {
                    images.put("optionalImageList", representImageList.subList(1, Math.min(representImageList.size(), 20))); // 추가 이미지 (최대 19개)
                }
            } else {
                throw new IllegalArgumentException("유효한 이미지 URL이 없습니다. 최소 1개 이상의 이미지가 필요합니다.");
            }
        } else {
            throw new IllegalArgumentException("이미지가 필수입니다. 최소 1개 이상의 이미지 URL을 제공해주세요.");
        }
        originProduct.put("images", images);
        
        // 판매 기간 (선택사항 - 현재 시간부터 1년 후까지)
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(koreaZone);
        ZonedDateTime oneYearLater = now.plusYears(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        originProduct.put("saleStartDate", now.format(formatter));
        originProduct.put("saleEndDate", oneYearLater.format(formatter));
        
        // 배송 정보
        Map<String, Object> deliveryInfo = new HashMap<>();
        String deliveryType = "DELIVERY"; // 기본값
        
        if (request.getDeliveryInfo() != null) {
            deliveryType = request.getDeliveryInfo().getDeliveryType() != null 
                    ? request.getDeliveryInfo().getDeliveryType() 
                    : "DELIVERY";
            deliveryInfo.put("deliveryType", deliveryType);
            deliveryInfo.put("deliveryAttributeType", "NORMAL"); // 일반 배송
            
            // DELIVERY일 때 deliveryCompany 필수 (택배사 코드)
            // 네이버 API의 정확한 택배사 코드는 "주문 > 발주/발송 처리 > 발송 처리 API" 참고 필요
            // 주요 택배사 코드: CJGLS(CJ대한통운), HANJIN(한진택배), KGB(로젠택배), HYUNDAI(롯데택배), EPOST(우체국택배)
            if ("DELIVERY".equals(deliveryType)) {
                String deliveryCompany = request.getDeliveryInfo().getDeliveryCompany();
                if (deliveryCompany != null && !deliveryCompany.trim().isEmpty()) {
                    deliveryInfo.put("deliveryCompany", deliveryCompany.trim());
                } else {
                    // 기본값: CJ대한통운 (CJGLS)
                    deliveryInfo.put("deliveryCompany", "CJGLS");
                }
            }
            
            Map<String, Object> deliveryFee = new HashMap<>();
            if (request.getDeliveryInfo().getDeliveryFee() != null && request.getDeliveryInfo().getDeliveryFee() > 0) {
                deliveryFee.put("deliveryFeeType", "CONDITIONAL_FREE"); // 조건부 무료
                Integer baseFee = request.getDeliveryInfo().getDeliveryFee();
                deliveryFee.put("baseFee", baseFee);
                
                // freeConditionalAmount는 baseFee 이상이어야 함 (네이버 API 요구사항)
                Integer freeConditionalAmount = request.getDeliveryInfo().getFreeDeliveryMinAmount();
                if (freeConditionalAmount == null || freeConditionalAmount < baseFee) {
                    // baseFee 이상으로 설정 (네이버 API 요구사항)
                    freeConditionalAmount = baseFee;
                    log.info("freeConditionalAmount가 baseFee({}) 미만이므로 baseFee로 설정합니다.", baseFee);
                }
                deliveryFee.put("freeConditionalAmount", freeConditionalAmount);
                
                // CONDITIONAL_FREE일 때도 deliveryFeePayType이 필요할 수 있음
                deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제
            } else {
                // 기본값: 일반 배송비 (4500원)
                deliveryFee.put("deliveryFeeType", "PAID"); // 유료 배송
                deliveryFee.put("baseFee", 4500); // 기본 배송비 4500원
                deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
            }
            deliveryInfo.put("deliveryFee", deliveryFee);
        } else {
            // 기본 배송 정보
            deliveryInfo.put("deliveryType", "DELIVERY");
            deliveryInfo.put("deliveryAttributeType", "NORMAL");
            // DELIVERY일 때 deliveryCompany 필수 - 기본값: CJ대한통운 (CJGLS)
            deliveryInfo.put("deliveryCompany", "CJGLS");
            
            Map<String, Object> deliveryFee = new HashMap<>();
            deliveryFee.put("deliveryFeeType", "PAID"); // 유료 배송
            deliveryFee.put("baseFee", 4500); // 기본 배송비 4500원
            deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
            deliveryInfo.put("deliveryFee", deliveryFee);
        }
        
        // 필수: claimDeliveryInfo (교환/반품 배송 정보)
        Map<String, Object> claimDeliveryInfo = new HashMap<>();
        claimDeliveryInfo.put("deliveryType", deliveryType);
        claimDeliveryInfo.put("deliveryAttributeType", "NORMAL");
        // claimDeliveryInfo에는 deliveryCompany가 필수가 아님 (문서에 명시되지 않음)
        Map<String, Object> claimDeliveryFee = new HashMap<>();
        claimDeliveryFee.put("deliveryFeeType", "FREE");
        claimDeliveryInfo.put("deliveryFee", claimDeliveryFee);
        // 필수: 반품/교환 배송비
        claimDeliveryInfo.put("returnDeliveryFee", 0); // 반품 배송비 (무료)
        claimDeliveryInfo.put("exchangeDeliveryFee", 0); // 교환 배송비 (무료)
        deliveryInfo.put("claimDeliveryInfo", claimDeliveryInfo);
        
        originProduct.put("deliveryInfo", deliveryInfo);
        
        // 상세 속성 (필수 필드 포함)
        Map<String, Object> detailAttribute = new HashMap<>();
        detailAttribute.put("A001", "일반상품"); // 상품 유형
        
        // 필수 필드들
        detailAttribute.put("minorPurchasable", false); // 미성년자 구매 가능 여부
        
        // A/S 정보 (객체 형식) - 필수 필드 포함
        Map<String, Object> afterServiceInfo = new HashMap<>();
        afterServiceInfo.put("possible", true); // A/S 가능 여부
        afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000"); // A/S 전화번호 (필수)
        afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다."); // A/S 안내 (필수)
        detailAttribute.put("afterServiceInfo", afterServiceInfo);
        
        // 필수: 상품정보제공고시 (기본값: 일반상품)
        Map<String, Object> productInfoProvidedNotice = new HashMap<>();
        productInfoProvidedNotice.put("productInfoProvidedNoticeType", "ETC"); // 기타
            // ETC 타입일 때 etc 필드 필수
            Map<String, Object> etc = new HashMap<>();
            etc.put("content", "상품정보제공고시 내용입니다."); // 기본 내용
            // 모델명: 엑셀에서 읽은 값 또는 상품명 사용
            etc.put("modelName", request.getModelName() != null && !request.getModelName().trim().isEmpty() 
                    ? request.getModelName().trim() 
                    : (request.getName() != null ? request.getName() : "일반상품")); // 모델명 (필수)
            etc.put("itemName", request.getName() != null ? request.getName() : "일반상품"); // 품목명 (필수)
            // 제조사: 엑셀에서 읽은 값 또는 기본값 사용
            etc.put("manufacturer", request.getManufacturer() != null && !request.getManufacturer().trim().isEmpty() 
                    ? request.getManufacturer().trim() 
                    : "제조사명"); // 제조사 (필수)
            etc.put("afterServiceDirector", "1588-0000"); // A/S 책임자 또는 소비자 상담 관련 전화번호 (필수)
            productInfoProvidedNotice.put("etc", etc);
        detailAttribute.put("productInfoProvidedNotice", productInfoProvidedNotice);
        
        // 어린이 인증 대상 카테고리 처리
        // 일부 카테고리(예: 어린이용품, 장난감 등)는 어린이 제품 인증이 필수
        // 에러 메시지: "어린이인증 대상 카테고리 상품은 카탈로그 입력이 필수입니다"
        // API 문서: '어린이제품 인증 대상' 카테고리 상품인 경우 childCertifiedProductExclusionYn 필수
        
        // KC 인증 대상 카테고리 처리
        // 일부 카테고리(예: 전기/전자 제품 등)는 KC 인증이 필수
        // 에러 메시지: "KC 인증대상 인증 종류를 선택하셔야 합니다."
        // API 문서에 따르면 productCertificationInfos 배열에 항목이 있고, 각 항목에 kindType이 필요함
        List<Map<String, Object>> productCertificationInfos = new ArrayList<>();
        
        // KC 인증이 필요한 카테고리인지 확인
        // 전기/전자 제품 카테고리 (예: 디지털/가전, 모니터 등)는 KC 인증이 필수일 수 있음
        String categoryPath = request.getCategoryPath();
        String leafCategoryId = request.getLeafCategoryId();
        boolean isKcCertificationRequired = false;
        boolean isChildCertificationRequired = false;
        
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
                isKcCertificationRequired = true;
                log.info("KC 인증 필요 카테고리 감지 (경로): {}", categoryPath);
            }
        }
        
        // 카테고리 ID로 확인 (더 정확한 범위)
        if (!isKcCertificationRequired && leafCategoryId != null) {
            try {
                long categoryIdNum = Long.parseLong(leafCategoryId);
                // 디지털/가전 카테고리 ID 범위 확장
                // 모니터: 50000153, 노트북: 50000151, PC: 50000089 등
                // 일부 카테고리는 50001594 같은 더 큰 범위에 있을 수 있음
                // 디지털/가전 관련 카테고리 ID 범위를 확장하여 더 많은 카테고리 포함
                // 50000003~50002000 범위로 확장 (일부 카테고리는 이 범위를 벗어날 수 있으므로 더 넓게 설정)
                if ((categoryIdNum >= 50000003L && categoryIdNum <= 50002000L) ||
                    (categoryIdNum >= 50001000L && categoryIdNum <= 50003000L)) {
                    isKcCertificationRequired = true;
                    log.info("KC 인증 필요 카테고리 감지 (ID): {}", leafCategoryId);
                }
            } catch (NumberFormatException e) {
                // 카테고리 ID가 숫자가 아니면 무시
            }
        }
        
        // 어린이제품 인증이 필요한 카테고리인지 확인
        // 출산/육아, 완구/인형, 유아동의류 등은 어린이제품 인증이 필수일 수 있음
        if (categoryPath != null && !categoryPath.trim().isEmpty()) {
            String lowerCategoryPath = categoryPath.toLowerCase();
            // 어린이제품 인증이 필요한 카테고리 키워드 확인
            if (lowerCategoryPath.contains("출산") || lowerCategoryPath.contains("육아") ||
                lowerCategoryPath.contains("완구") || lowerCategoryPath.contains("인형") ||
                lowerCategoryPath.contains("유아") || lowerCategoryPath.contains("아동") ||
                lowerCategoryPath.contains("어린이") || lowerCategoryPath.contains("키즈") ||
                lowerCategoryPath.contains("장난감") || lowerCategoryPath.contains("아기")) {
                isChildCertificationRequired = true;
                log.info("어린이제품 인증 필요 카테고리 감지 (경로): {}", categoryPath);
            }
        }
        
        // 카테고리 ID로 확인 (출산/육아 카테고리 ID 범위)
        if (!isChildCertificationRequired && leafCategoryId != null) {
            try {
                long categoryIdNum = Long.parseLong(leafCategoryId);
                // 출산/육아 카테고리 ID 범위 확인 (예: 50004000~50005000, 50016000~50017000 등)
                // 출산/육아 관련 카테고리 ID 범위를 확인하여 어린이제품 인증 필요 여부 판단
                if ((categoryIdNum >= 50004000L && categoryIdNum <= 50005000L) ||
                    (categoryIdNum >= 50016000L && categoryIdNum <= 50017000L) ||
                    (categoryIdNum >= 50016500L && categoryIdNum <= 50016700L)) {
                    isChildCertificationRequired = true;
                    log.info("어린이제품 인증 필요 카테고리 감지 (ID): {}", leafCategoryId);
                }
            } catch (NumberFormatException e) {
                // 카테고리 ID가 숫자가 아니면 무시
            }
        }
        
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
        
        detailAttribute.put("productCertificationInfos", productCertificationInfos);
        
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
            certificationTargetExcludeContent.put("kcCertifiedProductExclusionYn", "TRUE");
            log.info("certificationTargetExcludeContent 추가: kcCertifiedProductExclusionYn=TRUE (KC 인증 대상에서 제외)");
        } else {
            // KC 인증 대상이 아닌 것으로 판단되었지만, 안전을 위해 추가
            // 일부 카테고리는 감지되지 않을 수 있으므로 기본값으로 TRUE 설정
            certificationTargetExcludeContent.put("kcCertifiedProductExclusionYn", "TRUE");
            log.debug("certificationTargetExcludeContent 추가 (안전장치): kcCertifiedProductExclusionYn=TRUE");
        }
        
        // 어린이제품 인증 대상 카테고리 처리
        if (isChildCertificationRequired) {
            // 어린이제품 인증 대상 카테고리인 경우, certificationInfoId를 모르므로 인증 대상에서 제외 처리
            // childCertifiedProductExclusionYn: false(어린이제품 인증 대상), true(어린이제품 인증 대상 아님)
            // 미입력 시 false로 저장되므로, 인증 대상에서 제외하려면 true로 설정
            certificationTargetExcludeContent.put("childCertifiedProductExclusionYn", true);
            log.info("certificationTargetExcludeContent 추가: childCertifiedProductExclusionYn=true (어린이제품 인증 대상에서 제외)");
        } else {
            // 어린이제품 인증 대상이 아닌 것으로 판단되었지만, 안전을 위해 추가
            // 일부 카테고리는 감지되지 않을 수 있으므로 기본값으로 true 설정
            certificationTargetExcludeContent.put("childCertifiedProductExclusionYn", true);
            log.debug("certificationTargetExcludeContent 추가 (안전장치): childCertifiedProductExclusionYn=true");
        }
        
        detailAttribute.put("certificationTargetExcludeContent", certificationTargetExcludeContent);
        
        // 원산지 정보 (필수) - 카테고리별 자동 처리
        Map<String, Object> originAreaInfo = createOriginAreaInfo(
                request.getOriginArea(), 
                request.getCategoryPath()
        );
        // 해산물이 아닌 상품에서 해역명 필드가 포함되어 있으면 제거 (안전장치)
        if (originAreaInfo.containsKey("oceanName") || originAreaInfo.containsKey("oceanType") || originAreaInfo.containsKey("oceanArea")) {
            log.warn("해산물이 아닌 상품에 해역명 필드가 포함되어 있습니다. 제거합니다. originAreaInfo={}", originAreaInfo);
            originAreaInfo.remove("oceanName");
            originAreaInfo.remove("oceanType");
            originAreaInfo.remove("oceanArea");
        }
        // originAreaCode 확인 및 로깅
        if (!originAreaInfo.containsKey("originAreaCode")) {
            log.error("originAreaCode가 없습니다! originAreaInfo={}", originAreaInfo);
            // originAreaCode가 없으면 다시 생성
            String originArea = request.getOriginArea();
            if (originArea != null && !originArea.trim().isEmpty()) {
                String originAreaCode = determineOriginAreaCode(originArea.trim());
                originAreaInfo.put("originAreaCode", originAreaCode);
                log.warn("originAreaCode 재설정: originArea={}, originAreaCode={}", originArea, originAreaCode);
            }
        }
        log.debug("최종 originAreaInfo: {}", originAreaInfo);
        detailAttribute.put("originAreaInfo", originAreaInfo);
        
        originProduct.put("detailAttribute", detailAttribute);
        
        // 스마트스토어 채널상품 정보
        Map<String, Object> smartstoreChannelProduct = new HashMap<>();
        smartstoreChannelProduct.put("naverShoppingRegistration", 
                request.getNaverShoppingRegistration() != null ? request.getNaverShoppingRegistration() : false);
        // channelProductDisplayStatusType: 네이버 API 문서에 따르면 ON, SUSPENSION만 입력 가능합니다.
        // 가능한 값: WAIT(전시 대기), ON(전시 중), SUSPENSION(전시 중지)
        // DISPLAY, HIDE, OFF는 유효하지 않은 값입니다.
        String displayStatus = request.getChannelProductDisplayStatusType();
        if (displayStatus != null && !displayStatus.trim().isEmpty()) {
            String upper = displayStatus.trim().toUpperCase();
            if ("ON".equals(upper) || "SUSPENSION".equals(upper) || "WAIT".equals(upper)) {
                smartstoreChannelProduct.put("channelProductDisplayStatusType", upper);
            } else {
                // DISPLAY, HIDE, OFF 등은 유효하지 않으므로 ON으로 변환
                log.warn("channelProductDisplayStatusType '{}'는 유효하지 않습니다. ON으로 변환합니다. (유효한 값: ON, SUSPENSION)", displayStatus);
                smartstoreChannelProduct.put("channelProductDisplayStatusType", "ON");
            }
        } else {
            smartstoreChannelProduct.put("channelProductDisplayStatusType", "ON"); // 기본값: 전시 중
        }
        
        // 최종 구조
        naverData.put("originProduct", originProduct);
        naverData.put("smartstoreChannelProduct", smartstoreChannelProduct);
        
        return naverData;
    }
    
    /**
     * OriginProduct 객체를 Map으로 변환
     * 
     * @param originProduct OriginProduct 객체
     * @param categoryPath 카테고리 경로 (해산물 카테고리 판단용)
     * @param originAreaFromRequest ProductRequest의 originArea 필드 (우선 사용, null 가능)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOriginProductToMap(ProductRequest.OriginProduct originProduct, String categoryPath, String originAreaFromRequest) {
        Map<String, Object> map = new HashMap<>();
        
        if (originProduct.getStatusType() != null) {
            // 중요: 네이버 API 문서에 따르면 상품 등록 시에는 SALE(판매 중)만 입력할 수 있습니다.
            // OUTOFSTOCK(품절)은 재고가 0일 때 시스템이 자동으로 설정하는 상태입니다.
            String statusType = originProduct.getStatusType();
            String upper = statusType.trim().toUpperCase();
            if (!"SALE".equals(upper)) {
                log.warn("상품 등록 시에는 statusType '{}'를 사용할 수 없습니다. SALE로 변환합니다. (재고가 0이면 시스템이 자동으로 OUTOFSTOCK으로 설정합니다.)", statusType);
                statusType = "SALE";
            }
            map.put("statusType", statusType);
        }
        if (originProduct.getSaleType() != null) {
            map.put("saleType", originProduct.getSaleType());
        }
        if (originProduct.getLeafCategoryId() != null) {
            map.put("leafCategoryId", originProduct.getLeafCategoryId());
        }
        if (originProduct.getName() != null) {
            map.put("name", originProduct.getName());
        }
        if (originProduct.getDetailContent() != null) {
            map.put("detailContent", originProduct.getDetailContent());
        }
        if (originProduct.getImages() != null) {
            map.put("images", originProduct.getImages());
        }
        if (originProduct.getSaleStartDate() != null) {
            map.put("saleStartDate", originProduct.getSaleStartDate());
        }
        if (originProduct.getSaleEndDate() != null) {
            map.put("saleEndDate", originProduct.getSaleEndDate());
        }
        if (originProduct.getSalePrice() != null) {
            map.put("salePrice", originProduct.getSalePrice());
        }
        if (originProduct.getStockQuantity() != null) {
            map.put("stockQuantity", originProduct.getStockQuantity());
        }
        if (originProduct.getDeliveryInfo() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> deliveryInfoMap = (Map<String, Object>) originProduct.getDeliveryInfo();
            
            // deliveryType 확인
            String deliveryType = (String) deliveryInfoMap.getOrDefault("deliveryType", "DELIVERY");
            
            // DELIVERY일 때 deliveryCompany 필수 (없으면 기본값 사용)
            // 주요 택배사 코드: CJGLS(CJ대한통운), HANJIN(한진택배), KGB(로젠택배), HYUNDAI(롯데택배), EPOST(우체국택배)
            if ("DELIVERY".equals(deliveryType)) {
                Object deliveryCompany = deliveryInfoMap.get("deliveryCompany");
                if (deliveryCompany == null || deliveryCompany.toString().trim().isEmpty() || "CUSTOM".equals(deliveryCompany)) {
                    // 기본값: CJ대한통운 (CJGLS)
                    deliveryInfoMap.put("deliveryCompany", "CJGLS");
                }
            }
            
            // deliveryFee 처리: PAID와 CONDITIONAL_FREE 모두 처리
            if (deliveryInfoMap.containsKey("deliveryFee")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> deliveryFee = (Map<String, Object>) deliveryInfoMap.get("deliveryFee");
                if (deliveryFee != null) {
                    String deliveryFeeType = (String) deliveryFee.get("deliveryFeeType");
                    
                    // PAID일 때 deliveryFeePayType 필수 확인 및 추가
                    if ("PAID".equals(deliveryFeeType)) {
                        if (!deliveryFee.containsKey("deliveryFeePayType")) {
                            deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
                        }
                    }
                    
                    // CONDITIONAL_FREE일 때 deliveryFeePayType 필수 및 freeConditionalAmount 검증
                    if ("CONDITIONAL_FREE".equals(deliveryFeeType)) {
                        // deliveryFeePayType 필수 추가
                        if (!deliveryFee.containsKey("deliveryFeePayType")) {
                            deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
                        }
                        
                        // freeConditionalAmount가 baseFee 이상인지 검증
                        Object baseFeeObj = deliveryFee.get("baseFee");
                        Object freeConditionalAmountObj = deliveryFee.get("freeConditionalAmount");
                        
                        if (baseFeeObj != null && freeConditionalAmountObj != null) {
                            Integer baseFee = null;
                            Integer freeConditionalAmount = null;
                            
                            // baseFee 변환
                            if (baseFeeObj instanceof Integer) {
                                baseFee = (Integer) baseFeeObj;
                            } else if (baseFeeObj instanceof Number) {
                                baseFee = ((Number) baseFeeObj).intValue();
                            }
                            
                            // freeConditionalAmount 변환
                            if (freeConditionalAmountObj instanceof Integer) {
                                freeConditionalAmount = (Integer) freeConditionalAmountObj;
                            } else if (freeConditionalAmountObj instanceof Number) {
                                freeConditionalAmount = ((Number) freeConditionalAmountObj).intValue();
                            }
                            
                            // freeConditionalAmount가 baseFee 미만이면 baseFee로 설정
                            if (baseFee != null && freeConditionalAmount != null && freeConditionalAmount < baseFee) {
                                deliveryFee.put("freeConditionalAmount", baseFee);
                                log.info("freeConditionalAmount가 baseFee({}) 미만이므로 baseFee로 설정합니다.", baseFee);
                            }
                        }
                    }
                }
            }
            
            // claimDeliveryInfo가 없으면 추가
            if (!deliveryInfoMap.containsKey("claimDeliveryInfo")) {
                Map<String, Object> claimDeliveryInfo = new HashMap<>();
                claimDeliveryInfo.put("deliveryType", deliveryType);
                claimDeliveryInfo.put("deliveryAttributeType", "NORMAL");
                // claimDeliveryInfo에는 deliveryCompany가 필수가 아님
                Map<String, Object> claimDeliveryFee = new HashMap<>();
                claimDeliveryFee.put("deliveryFeeType", "FREE");
                claimDeliveryInfo.put("deliveryFee", claimDeliveryFee);
                // 필수: 반품/교환 배송비
                claimDeliveryInfo.put("returnDeliveryFee", 0); // 반품 배송비 (무료)
                claimDeliveryInfo.put("exchangeDeliveryFee", 0); // 교환 배송비 (무료)
                deliveryInfoMap.put("claimDeliveryInfo", claimDeliveryInfo);
            } else {
                // claimDeliveryInfo가 있으면 필수 필드 확인 및 추가
                @SuppressWarnings("unchecked")
                Map<String, Object> claimDeliveryInfo = (Map<String, Object>) deliveryInfoMap.get("claimDeliveryInfo");
                if (claimDeliveryInfo != null) {
                    if (!claimDeliveryInfo.containsKey("returnDeliveryFee")) {
                        claimDeliveryInfo.put("returnDeliveryFee", 0);
                    }
                    if (!claimDeliveryInfo.containsKey("exchangeDeliveryFee")) {
                        claimDeliveryInfo.put("exchangeDeliveryFee", 0);
                    }
                }
            }
            
            map.put("deliveryInfo", deliveryInfoMap);
        } else {
            // deliveryInfo가 없으면 기본값 추가
            Map<String, Object> deliveryInfo = new HashMap<>();
            deliveryInfo.put("deliveryType", "DELIVERY");
            deliveryInfo.put("deliveryAttributeType", "NORMAL");
            // DELIVERY일 때 deliveryCompany 필수 - 기본값: CJ대한통운 (CJGLS)
            deliveryInfo.put("deliveryCompany", "CJGLS");
            Map<String, Object> deliveryFee = new HashMap<>();
            deliveryFee.put("deliveryFeeType", "PAID"); // 유료 배송
            deliveryFee.put("baseFee", 4500); // 기본 배송비 4500원
            deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
            deliveryInfo.put("deliveryFee", deliveryFee);
            
            Map<String, Object> claimDeliveryInfo = new HashMap<>();
            claimDeliveryInfo.put("deliveryType", "DELIVERY");
            claimDeliveryInfo.put("deliveryAttributeType", "NORMAL");
            // claimDeliveryInfo에는 deliveryCompany가 필수가 아님
            Map<String, Object> claimDeliveryFee = new HashMap<>();
            claimDeliveryFee.put("deliveryFeeType", "FREE");
            claimDeliveryInfo.put("deliveryFee", claimDeliveryFee);
            // 필수: 반품/교환 배송비
            claimDeliveryInfo.put("returnDeliveryFee", 0); // 반품 배송비 (무료)
            claimDeliveryInfo.put("exchangeDeliveryFee", 0); // 교환 배송비 (무료)
            deliveryInfo.put("claimDeliveryInfo", claimDeliveryInfo);
            
            map.put("deliveryInfo", deliveryInfo);
        }
        
        if (originProduct.getProductLogistics() != null) {
            map.put("productLogistics", originProduct.getProductLogistics());
        }
        
        if (originProduct.getDetailAttribute() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailAttributeMap = (Map<String, Object>) originProduct.getDetailAttribute();
            
            // 필수 필드들이 없으면 추가
            if (!detailAttributeMap.containsKey("minorPurchasable")) {
                detailAttributeMap.put("minorPurchasable", false);
            }
            
            // afterServiceInfo가 없거나 문자열이면 객체로 변환
            if (!detailAttributeMap.containsKey("afterServiceInfo")) {
                Map<String, Object> afterServiceInfo = new HashMap<>();
                afterServiceInfo.put("possible", true);
                afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000"); // A/S 전화번호 (필수)
                afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다."); // A/S 안내 (필수)
                detailAttributeMap.put("afterServiceInfo", afterServiceInfo);
            } else {
                Object afterServiceInfoObj = detailAttributeMap.get("afterServiceInfo");
                // 문자열인 경우 객체로 변환
                if (afterServiceInfoObj instanceof String) {
                    Map<String, Object> afterServiceInfo = new HashMap<>();
                    afterServiceInfo.put("possible", "Y".equalsIgnoreCase((String) afterServiceInfoObj) || "true".equalsIgnoreCase((String) afterServiceInfoObj));
                    afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000"); // A/S 전화번호 (필수)
                    afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다."); // A/S 안내 (필수)
                    detailAttributeMap.put("afterServiceInfo", afterServiceInfo);
                } else if (afterServiceInfoObj instanceof Map) {
                    // 이미 객체인 경우 필수 필드 확인 및 추가
                    @SuppressWarnings("unchecked")
                    Map<String, Object> afterServiceInfo = (Map<String, Object>) afterServiceInfoObj;
                    if (!afterServiceInfo.containsKey("afterServiceTelephoneNumber")) {
                        afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000");
                    }
                    if (!afterServiceInfo.containsKey("afterServiceGuideContent")) {
                        afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다.");
                    }
                }
            }
            
            // 필수: 상품정보제공고시
            if (!detailAttributeMap.containsKey("productInfoProvidedNotice")) {
                Map<String, Object> productInfoProvidedNotice = new HashMap<>();
                productInfoProvidedNotice.put("productInfoProvidedNoticeType", "ETC"); // 기타
                // ETC 타입일 때 etc 필드 필수
                Map<String, Object> etc = new HashMap<>();
                etc.put("content", "상품정보제공고시 내용입니다."); // 기본 내용
                etc.put("modelName", "일반상품"); // 모델명 (필수)
                etc.put("itemName", "일반상품"); // 품목명 (필수)
                etc.put("manufacturer", "제조사명"); // 제조사 (필수)
                productInfoProvidedNotice.put("etc", etc);
                detailAttributeMap.put("productInfoProvidedNotice", productInfoProvidedNotice);
            } else {
                // productInfoProvidedNotice가 있으면 etc 필드 확인 및 추가
                @SuppressWarnings("unchecked")
                Map<String, Object> productInfoProvidedNotice = (Map<String, Object>) detailAttributeMap.get("productInfoProvidedNotice");
                if (productInfoProvidedNotice != null) {
                    String noticeType = (String) productInfoProvidedNotice.get("productInfoProvidedNoticeType");
                    if ("ETC".equals(noticeType)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> etc = (Map<String, Object>) productInfoProvidedNotice.get("etc");
                        if (etc == null) {
                            etc = new HashMap<>();
                            productInfoProvidedNotice.put("etc", etc);
                        }
                        // 필수 필드 확인 및 추가
                        if (!etc.containsKey("content")) {
                            etc.put("content", "상품정보제공고시 내용입니다.");
                        }
                        if (!etc.containsKey("modelName")) {
                            etc.put("modelName", "일반상품");
                        }
                        if (!etc.containsKey("itemName")) {
                            etc.put("itemName", "일반상품");
                        }
                        if (!etc.containsKey("manufacturer")) {
                            etc.put("manufacturer", "제조사명");
                        }
                        if (!etc.containsKey("afterServiceDirector")) {
                            etc.put("afterServiceDirector", "1588-0000"); // A/S 책임자 또는 소비자 상담 관련 전화번호 (필수)
                        }
                    }
                }
            }
            
            // originAreaInfo 처리: createOriginAreaInfo를 사용하여 올바르게 처리
            String originAreaToUse = null;
            
            // 1. request의 originArea가 있으면 우선 사용 (가장 신뢰할 수 있음)
            if (originAreaFromRequest != null && !originAreaFromRequest.trim().isEmpty()) {
                originAreaToUse = originAreaFromRequest.trim();
                log.info("ProductRequest의 originArea 사용: {}", originAreaToUse);
            }
            
            // 2. originAreaInfo가 없으면 기본값 생성
            if (!detailAttributeMap.containsKey("originAreaInfo")) {
                if (originAreaToUse == null) {
                    originAreaToUse = "국내산";
                }
                Map<String, Object> originAreaInfo = createOriginAreaInfo(originAreaToUse, categoryPath);
                detailAttributeMap.put("originAreaInfo", originAreaInfo);
            } else {
                // originAreaInfo가 이미 있으면 createOriginAreaInfo를 사용하여 재생성
                @SuppressWarnings("unchecked")
                Map<String, Object> existingOriginAreaInfo = (Map<String, Object>) detailAttributeMap.get("originAreaInfo");
                if (existingOriginAreaInfo != null) {
                    // request의 originArea가 없으면 기존 originAreaInfo에서 추출
                    if (originAreaToUse == null) {
                        // 기존 originAreaInfo에서 원산지 정보 추출
                        // 1. importCountry가 있으면 해외 원산지이므로 그것을 사용
                        // 2. 없으면 content 사용
                        // 3. 둘 다 없으면 originAreaCode 확인 (00이 아니면 해외 원산지로 추정)
                        Object importCountryObj = existingOriginAreaInfo.get("importCountry");
                        Object contentObj = existingOriginAreaInfo.get("content");
                        Object originAreaCodeObj = existingOriginAreaInfo.get("originAreaCode");
                        
                        if (importCountryObj != null && !importCountryObj.toString().trim().isEmpty()) {
                            // importCountry가 있으면 해외 원산지 (API 문서에는 없지만 기존 데이터 호환성)
                            originAreaToUse = importCountryObj.toString().trim();
                            log.info("기존 originAreaInfo에서 importCountry 추출: {}", originAreaToUse);
                        } else if (contentObj != null && !contentObj.toString().trim().isEmpty()) {
                            // content 사용
                            originAreaToUse = contentObj.toString().trim();
                            log.info("기존 originAreaInfo에서 content 추출: {}", originAreaToUse);
                        } else if (originAreaCodeObj != null && !"00".equals(originAreaCodeObj.toString().trim())) {
                            // originAreaCode가 "00"(국산)이 아니면 해외 원산지로 추정
                            // 하지만 원산지 이름을 알 수 없으므로 기본값 사용
                            originAreaToUse = "해외산";
                            log.warn("기존 originAreaInfo에서 원산지 이름을 찾을 수 없지만 originAreaCode={}이므로 해외 원산지로 추정", 
                                    originAreaCodeObj.toString().trim());
                        } else {
                            // 둘 다 없으면 기본값
                            originAreaToUse = "국내산";
                            log.warn("기존 originAreaInfo에서 원산지 정보를 찾을 수 없어 기본값(국내산) 사용");
                        }
                    }
                    
                    // createOriginAreaInfo를 사용하여 올바르게 재생성 (카테고리 경로 고려)
                    // 이렇게 하면 해산물이 아닌 경우 oceanName/oceanType 제거,
                    // 해외 원산지인 경우 importCountry 추가됨 (importer는 제외)
                    // 기존 originAreaInfo에 해역명 필드가 있을 수 있으므로 재생성하여 제거
                    Map<String, Object> originAreaInfo = createOriginAreaInfo(originAreaToUse, categoryPath);
                    detailAttributeMap.put("originAreaInfo", originAreaInfo);
                    log.info("originAreaInfo 재생성 완료: originArea={}, categoryPath={}, 재생성된 originAreaInfo={}", 
                            originAreaToUse, categoryPath, originAreaInfo);
                } else {
                    // null인 경우 기본값 생성
                    if (originAreaToUse == null) {
                        originAreaToUse = "국내산";
                    }
                    Map<String, Object> originAreaInfo = createOriginAreaInfo(originAreaToUse, categoryPath);
                    detailAttributeMap.put("originAreaInfo", originAreaInfo);
                }
            }
            
            map.put("detailAttribute", detailAttributeMap);
        } else {
            // detailAttribute가 없으면 기본값 추가
            Map<String, Object> detailAttribute = new HashMap<>();
            detailAttribute.put("A001", "일반상품");
            detailAttribute.put("minorPurchasable", false);
            
            // A/S 정보 (객체 형식) - 필수 필드 포함
            Map<String, Object> afterServiceInfo = new HashMap<>();
            afterServiceInfo.put("possible", true);
            afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000"); // A/S 전화번호 (필수)
            afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다."); // A/S 안내 (필수)
            detailAttribute.put("afterServiceInfo", afterServiceInfo);
            
            // originAreaInfo는 createOriginAreaInfo를 사용하여 생성
            Map<String, Object> originAreaInfo = createOriginAreaInfo("국내산", null);
            detailAttribute.put("originAreaInfo", originAreaInfo);
            
            // 필수: 상품정보제공고시
            Map<String, Object> productInfoProvidedNotice = new HashMap<>();
            productInfoProvidedNotice.put("productInfoProvidedNoticeType", "ETC"); // 기타
            // ETC 타입일 때 etc 필드 필수
            Map<String, Object> etc = new HashMap<>();
            etc.put("content", "상품정보제공고시 내용입니다."); // 기본 내용
            etc.put("modelName", "일반상품"); // 모델명 (필수)
            etc.put("itemName", "일반상품"); // 품목명 (필수)
            etc.put("manufacturer", "제조사명"); // 제조사 (필수)
            etc.put("afterServiceDirector", "1588-0000"); // A/S 책임자 또는 소비자 상담 관련 전화번호 (필수)
            productInfoProvidedNotice.put("etc", etc);
            detailAttribute.put("productInfoProvidedNotice", productInfoProvidedNotice);
            
            map.put("detailAttribute", detailAttribute);
        }
        
        if (originProduct.getCustomerBenefit() != null) {
            map.put("customerBenefit", originProduct.getCustomerBenefit());
        }
        
        return map;
    }
    
    /**
     * 네이버 스마트스토어에 그룹상품 등록
     * 여러 개의 상품을 하나의 그룹으로 묶어 등록
     * 
     * @param storeId 스토어 ID
     * @param request 그룹상품 등록 요청 데이터
     * @return 등록된 그룹상품 정보
     */
    public Map<String, Object> createGroupProduct(Long storeId, GroupProductRequest request) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        int productCount = 0;
        if (request.getGroupProduct() != null && request.getGroupProduct().getSpecificProducts() != null) {
            productCount = request.getGroupProduct().getSpecificProducts().size();
        } else if (request.getSpecificProducts() != null) {
            productCount = request.getSpecificProducts().size();
        }
        
        log.info("=== 그룹상품 등록 시작 ===");
        log.info("스토어: {}, 상품 개수: {}", store.getStoreName(), productCount);
        log.info("요청 데이터 - groupProduct 존재: {}, groupName: {}, leafCategoryId: {}, guideId: {}, specificProducts 개수: {}", 
                request.getGroupProduct() != null,
                request.getGroupName(),
                request.getLeafCategoryId(),
                request.getGuideId(),
                request.getSpecificProducts() != null ? request.getSpecificProducts().size() : 0);
        log.info("요청 데이터 - smartstoreGroupChannel: {}, windowGroupChannel: {}", 
                request.getSmartstoreGroupChannel() != null,
                request.getWindowGroupChannel() != null);
        if (request.getSmartstoreGroupChannel() != null) {
            log.info("smartstoreGroupChannel.bbsSeq: {}", request.getSmartstoreGroupChannel().getBbsSeq());
        }
        
        // 이미지가 있으면 먼저 네이버 서버에 업로드
        if (request.getSpecificProducts() != null) {
            log.info("그룹상품 이미지 업로드 시작");
            for (GroupProductRequest.SpecificProduct sp : request.getSpecificProducts()) {
                if (sp.getImageUrls() != null && !sp.getImageUrls().isEmpty()) {
                    log.info("specificProduct 이미지 업로드 시작: {}개 이미지", sp.getImageUrls().size());
                    List<String> uploadedImageUrls = new ArrayList<>();
                    
                    for (String imageUrl : sp.getImageUrls()) {
                        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                            try {
                                // 네이버 이미지 업로드 API 호출
                                Map<String, Object> uploadResponse = naverCommerceClient.uploadProductImage(
                                        store.getAccessToken(),
                                        store.getVendorId(),
                                        imageUrl.trim()
                                ).block();
                                
                                // 업로드된 이미지 URL 추출
                                String uploadedUrl = null;
                                if (uploadResponse != null) {
                                    log.debug("이미지 업로드 응답: {}", uploadResponse);
                                    
                                    // 네이버 API 응답 구조에 따라 URL 추출 시도
                                    if (uploadResponse.containsKey("url")) {
                                        uploadedUrl = (String) uploadResponse.get("url");
                                    } else if (uploadResponse.containsKey("imageUrl")) {
                                        uploadedUrl = (String) uploadResponse.get("imageUrl");
                                    } else if (uploadResponse.containsKey("data")) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> data = (Map<String, Object>) uploadResponse.get("data");
                                        if (data != null) {
                                            if (data.containsKey("url")) {
                                                uploadedUrl = (String) data.get("url");
                                            } else if (data.containsKey("imageUrl")) {
                                                uploadedUrl = (String) data.get("imageUrl");
                                            } else if (data.containsKey("images") && data.get("images") instanceof List) {
                                                @SuppressWarnings("unchecked")
                                                List<Map<String, Object>> images = (List<Map<String, Object>>) data.get("images");
                                                if (!images.isEmpty() && images.get(0).containsKey("url")) {
                                                    uploadedUrl = (String) images.get(0).get("url");
                                                }
                                            }
                                        }
                                    } else if (uploadResponse.containsKey("images") && uploadResponse.get("images") instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> images = (List<Map<String, Object>>) uploadResponse.get("images");
                                        if (!images.isEmpty() && images.get(0).containsKey("url")) {
                                            uploadedUrl = (String) images.get(0).get("url");
                                        }
                                    } else if (uploadResponse.containsKey("imageUrls") && uploadResponse.get("imageUrls") instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<String> imageUrls = (List<String>) uploadResponse.get("imageUrls");
                                        if (!imageUrls.isEmpty()) {
                                            uploadedUrl = imageUrls.get(0);
                                        }
                                    }
                                }
                                
                                if (uploadedUrl != null && !uploadedUrl.isEmpty()) {
                                    uploadedImageUrls.add(uploadedUrl);
                                    log.info("이미지 업로드 성공: {} -> {}", imageUrl, uploadedUrl);
                                } else {
                                    log.error("이미지 업로드 응답에서 URL을 찾을 수 없습니다. 응답: {}", uploadResponse);
                                    throw new IllegalStateException("이미지 업로드 응답에서 URL을 찾을 수 없습니다. 네이버 서버에 이미지를 업로드해야 합니다.");
                                }
                            } catch (Exception e) {
                                log.error("이미지 업로드 실패: {}", imageUrl, e);
                                // 이미지 업로드가 실패하면 상품 등록을 중단 (네이버는 외부 URL을 허용하지 않음)
                                throw new IllegalStateException("이미지 업로드 실패: " + imageUrl + ". 네이버 서버에 이미지를 업로드해야 합니다.", e);
                            }
                        }
                    }
                    
                    // 업로드된 이미지 URL로 교체
                    sp.setImageUrls(uploadedImageUrls);
                    log.info("specificProduct 이미지 업로드 완료: {}개 이미지", uploadedImageUrls.size());
                }
            }
            log.info("그룹상품 이미지 업로드 완료");
        }
        
        // 네이버 API 형식으로 변환
        Map<String, Object> naverGroupProductData = convertToNaverGroupProductFormat(request);
        
        // 변환된 데이터 확인
        log.info("=== 변환 완료 ===");
        log.info("최상위 키: {}", naverGroupProductData.keySet());
        log.info("smartstoreChannelProduct 존재: {}", naverGroupProductData.containsKey("smartstoreChannelProduct"));
        log.info("windowChannelProduct 존재: {}", naverGroupProductData.containsKey("windowChannelProduct"));
        if (naverGroupProductData.containsKey("smartstoreChannelProduct")) {
            log.info("smartstoreChannelProduct 내용: {}", naverGroupProductData.get("smartstoreChannelProduct"));
        }
        if (naverGroupProductData.containsKey("windowChannelProduct")) {
            log.info("windowChannelProduct 내용: {}", naverGroupProductData.get("windowChannelProduct"));
        }
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.createGroupProduct(
                store.getAccessToken(),
                store.getVendorId(),
                naverGroupProductData
        ).block(); // 비동기를 동기로 변환
        
        log.info("그룹상품 등록 완료: 스토어={}", store.getStoreName());
        
        return result;
    }
    
    /**
     * 브랜드 조회
     * 전체 브랜드 목록을 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @return 브랜드 목록 (배열을 Map으로 래핑)
     */
    public Map<String, Object> getProductBrands(Long storeId) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("브랜드 조회: 스토어={}", store.getStoreName());
        
        // 네이버 API 호출 (동기 방식) - 배열로 반환됨
        List<Map<String, Object>> brandsList = naverCommerceClient.getProductBrands(
                store.getAccessToken(),
                store.getVendorId()
        ).block();
        
        // 배열을 Map으로 래핑하여 반환
        Map<String, Object> result = new HashMap<>();
        result.put("brands", brandsList != null ? brandsList : new ArrayList<>());
        
        log.info("브랜드 조회 완료: 스토어={}, 브랜드 개수={}", store.getStoreName(), 
                brandsList != null ? brandsList.size() : 0);
        
        return result;
    }
    
    /**
     * 전체 사이즈 타입 조회
     * 전체 사이즈 타입 목록을 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @return 사이즈 타입 목록 (배열을 Map으로 래핑)
     */
    public Map<String, Object> getProductSizes(Long storeId) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("사이즈 타입 조회: 스토어={}", store.getStoreName());
        
        // 네이버 API 호출 (동기 방식) - 배열로 반환됨
        List<Map<String, Object>> sizesList = naverCommerceClient.getProductSizes(
                store.getAccessToken(),
                store.getVendorId()
        ).block();
        
        // 배열을 Map으로 래핑하여 반환
        Map<String, Object> result = new HashMap<>();
        result.put("sizes", sizesList != null ? sizesList : new ArrayList<>());
        
        log.info("사이즈 타입 조회 완료: 스토어={}, 사이즈 타입 개수={}", store.getStoreName(), 
                sizesList != null ? sizesList.size() : 0);
        
        return result;
    }
    
    /**
     * 상품 목록 조회
     * 검색 조건에 따라 상품 목록을 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @param searchRequest 검색 요청 데이터
     * @return 상품 목록
     */
    public Map<String, Object> searchProducts(Long storeId, com.example.auto.dto.ProductSearchRequest searchRequest) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("상품 목록 조회: 스토어={}, searchKeywordType={}, page={}, size={}", 
                store.getStoreName(), 
                searchRequest.getSearchKeywordType(),
                searchRequest.getPage(),
                searchRequest.getSize());
        
        // DTO를 Map으로 변환
        Map<String, Object> searchMap = convertSearchRequestToMap(searchRequest);
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.searchProducts(
                store.getAccessToken(),
                store.getVendorId(),
                searchMap
        ).block();
        
        log.info("상품 목록 조회 완료: 스토어={}", store.getStoreName());
        
        return result;
    }
    
    /**
     * ProductSearchRequest를 Map으로 변환
     */
    private Map<String, Object> convertSearchRequestToMap(com.example.auto.dto.ProductSearchRequest request) {
        Map<String, Object> map = new HashMap<>();
        
        if (request.getSearchKeywordType() != null) {
            map.put("searchKeywordType", request.getSearchKeywordType());
        }
        if (request.getChannelProductNos() != null && !request.getChannelProductNos().isEmpty()) {
            map.put("channelProductNos", request.getChannelProductNos());
        }
        if (request.getOriginProductNos() != null && !request.getOriginProductNos().isEmpty()) {
            map.put("originProductNos", request.getOriginProductNos());
        }
        if (request.getGroupProductNos() != null && !request.getGroupProductNos().isEmpty()) {
            map.put("groupProductNos", request.getGroupProductNos());
        }
        if (request.getSellerManagementCode() != null && !request.getSellerManagementCode().trim().isEmpty()) {
            map.put("sellerManagementCode", request.getSellerManagementCode().trim());
        }
        if (request.getProductStatusTypes() != null && !request.getProductStatusTypes().isEmpty()) {
            map.put("productStatusTypes", request.getProductStatusTypes());
        }
        if (request.getPage() != null) {
            map.put("page", request.getPage());
        }
        if (request.getSize() != null) {
            map.put("size", request.getSize());
        }
        if (request.getOrderType() != null && !request.getOrderType().trim().isEmpty()) {
            map.put("orderType", request.getOrderType());
        }
        if (request.getPeriodType() != null && !request.getPeriodType().trim().isEmpty()) {
            map.put("periodType", request.getPeriodType());
        }
        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            map.put("fromDate", request.getFromDate().trim());
        }
        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            map.put("toDate", request.getToDate().trim());
        }
        
        return map;
    }
    
    /**
     * 원상품 조회
     * 원상품 번호로 원상품 정보를 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @param originProductNo 원상품 번호
     * @return 원상품 정보
     */
    public Map<String, Object> getOriginProduct(Long storeId, Long originProductNo) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("원상품 조회: 스토어={}, originProductNo={}", store.getStoreName(), originProductNo);
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.getOriginProduct(
                store.getAccessToken(),
                store.getVendorId(),
                originProductNo
        ).block();
        
        log.info("원상품 조회 완료: 스토어={}, originProductNo={}", store.getStoreName(), originProductNo);
        
        return result;
    }
    
    /**
     * 그룹상품 요청 결과 조회
     * 그룹상품 등록/수정 API의 처리 상태를 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @param type 요청 타입 (CREATE: 그룹상품 등록, UPDATE: 그룹상품 수정)
     * @param requestId 조회할 요청 ID
     * @return 그룹상품 요청 결과
     */
    public Map<String, Object> getGroupProductStatus(Long storeId, String type, String requestId) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("그룹상품 요청 결과 조회: 스토어={}, type={}, requestId={}", 
                store.getStoreName(), type, requestId);
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.getGroupProductStatus(
                store.getAccessToken(),
                store.getVendorId(),
                type,
                requestId
        ).block();
        
        log.info("그룹상품 요청 결과 조회 완료: 스토어={}", store.getStoreName());
        
        return result;
    }
    
    /**
     * 그룹상품 요청을 네이버 API 형식으로 변환
     * 실제 API 구조: { "groupProduct": { ... } }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToNaverGroupProductFormat(GroupProductRequest request) {
        log.debug("=== convertToNaverGroupProductFormat 시작 ===");
        Map<String, Object> naverData = new HashMap<>();
        Map<String, Object> groupProduct = new HashMap<>();
        
        // 네이버 API 형식으로 이미 들어온 경우
        if (request.getGroupProduct() != null) {
            log.debug("네이버 API 형식으로 들어온 요청 처리");
            GroupProductRequest.GroupProduct gp = request.getGroupProduct();
            
            // 필수 필드
            if (gp.getLeafCategoryId() != null) {
                groupProduct.put("leafCategoryId", gp.getLeafCategoryId());
            }
            if (gp.getName() != null) {
                groupProduct.put("name", gp.getName());
            }
            if (gp.getGuideId() != null) {
                groupProduct.put("guideId", gp.getGuideId());
            }
            
            // 선택 필드
            if (gp.getBrandName() != null) {
                groupProduct.put("brandName", gp.getBrandName());
            }
            if (gp.getBrandId() != null) {
                groupProduct.put("brandId", gp.getBrandId());
            }
            if (gp.getManufacturerName() != null) {
                groupProduct.put("manufacturerName", gp.getManufacturerName());
            }
            if (gp.getItselfProductionProductYn() != null) {
                groupProduct.put("itselfProductionProductYn", gp.getItselfProductionProductYn());
            }
            if (gp.getTaxType() != null) {
                groupProduct.put("taxType", gp.getTaxType());
            }
            if (gp.getSaleType() != null) {
                groupProduct.put("saleType", gp.getSaleType());
            }
            if (gp.getMinorPurchasable() != null) {
                groupProduct.put("minorPurchasable", gp.getMinorPurchasable());
            }
            if (gp.getBrandCertificationYn() != null) {
                groupProduct.put("brandCertificationYn", gp.getBrandCertificationYn());
            }
            if (gp.getProductInfoProvidedNotice() != null) {
                groupProduct.put("productInfoProvidedNotice", gp.getProductInfoProvidedNotice());
            }
            if (gp.getAfterServiceInfo() != null) {
                groupProduct.put("afterServiceInfo", gp.getAfterServiceInfo());
            }
            if (gp.getSellerCommentContent() != null) {
                groupProduct.put("sellerCommentContent", gp.getSellerCommentContent());
            }
            if (gp.getSupplementProductInfo() != null) {
                groupProduct.put("supplementProductInfo", gp.getSupplementProductInfo());
            }
            if (gp.getSeoInfo() != null) {
                groupProduct.put("seoInfo", gp.getSeoInfo());
            }
            if (gp.getCommonDetailContent() != null) {
                groupProduct.put("commonDetailContent", gp.getCommonDetailContent());
            }
            if (gp.getProductSize() != null) {
                groupProduct.put("productSize", gp.getProductSize());
            }
            
            // 채널 정보 변환 (각 specificProduct에 포함하기 위해)
            Map<String, Object> windowChannel = convertWindowGroupChannelToMap(gp.getWindowGroupChannel());
            Map<String, Object> smartstoreChannel = convertSmartstoreGroupChannelToMap(gp.getSmartstoreGroupChannel());
            
            // 공통 상세정보 추출 (첫 번째 specificProduct의 detailContent 사용)
            String commonDetailContent = null;
            if (gp.getSpecificProducts() != null && !gp.getSpecificProducts().isEmpty()) {
                GroupProductRequest.SpecificProduct firstSp = gp.getSpecificProducts().get(0);
                if (firstSp.getDetailContent() != null && !firstSp.getDetailContent().trim().isEmpty()) {
                    commonDetailContent = firstSp.getDetailContent();
                    log.debug("공통 상세정보 추출: 첫 번째 상품의 detailContent 사용");
                }
            }
            // 또는 groupProduct의 commonDetailContent 사용
            if (commonDetailContent == null && gp.getCommonDetailContent() != null) {
                commonDetailContent = gp.getCommonDetailContent();
                log.debug("공통 상세정보 추출: groupProduct.commonDetailContent 사용");
            }
            
            // specificProducts 변환 (각 specificProduct에 채널 정보 포함)
            if (gp.getSpecificProducts() != null && !gp.getSpecificProducts().isEmpty()) {
                List<Map<String, Object>> specificProductsList = new ArrayList<>();
                for (GroupProductRequest.SpecificProduct sp : gp.getSpecificProducts()) {
                    Map<String, Object> spMap = convertSpecificProductToMap(sp);
                    // 각 specificProduct에 채널 정보 추가
                    if (windowChannel != null && !windowChannel.isEmpty()) {
                        spMap.put("windowChannelProduct", windowChannel);
                        log.debug("specificProduct에 windowChannelProduct 추가");
                    } else {
                        // smartstoreChannelProduct는 항상 생성됨 (필수 필드 포함)
                        spMap.put("smartstoreChannelProduct", smartstoreChannel);
                        log.debug("specificProduct에 smartstoreChannelProduct 추가");
                    }
                    specificProductsList.add(spMap);
                }
                groupProduct.put("specificProducts", specificProductsList);
            }
            
            // groupProduct에 commonDetailContent 추가 (필수)
            if (commonDetailContent != null && !commonDetailContent.trim().isEmpty()) {
                groupProduct.put("commonDetailContent", commonDetailContent);
                log.debug("groupProduct에 commonDetailContent 추가");
            }
            
            // groupProduct 안에 smartstoreGroupChannel 추가
            // bbsSeq는 선택 필드이지만, 유효하지 않은 값이면 빈 객체로 보냄
            Map<String, Object> smartstoreGroupChannelInGroup = new HashMap<>();
            // bbsSeq는 공지사항 게시글 일련번호로, 실제 존재하는 값이어야 함
            // 유효하지 않은 값이면 빈 객체로 보내는 것이 안전함
            // 사용자가 명시적으로 bbsSeq를 제공하지 않으면 빈 객체로 전송
            groupProduct.put("smartstoreGroupChannel", smartstoreGroupChannelInGroup);
            log.debug("groupProduct 안에 smartstoreGroupChannel 추가 (빈 객체): {}", smartstoreGroupChannelInGroup);
            
            naverData.put("groupProduct", groupProduct);
            
            // 최상위 레벨에는 채널 정보를 넣지 않음 (각 specificProduct에 포함됨)
            log.info("[최종 확인] 최상위 레벨 키: {}", naverData.keySet());
            
            return naverData;
        }
        
        // 사용자 친화적 형식으로 들어온 경우
        if (request.getLeafCategoryId() != null && request.getGuideId() != null && 
            request.getSpecificProducts() != null && !request.getSpecificProducts().isEmpty()) {
            log.debug("사용자 친화적 형식으로 들어온 요청 처리");
            
            // 필수 필드
            groupProduct.put("leafCategoryId", request.getLeafCategoryId());
            groupProduct.put("name", request.getGroupName() != null ? request.getGroupName() : "그룹상품");
            groupProduct.put("guideId", request.getGuideId());
            
            // 기본값
            groupProduct.put("saleType", "NEW");
            groupProduct.put("taxType", "TAX");
            groupProduct.put("minorPurchasable", false);
            groupProduct.put("itselfProductionProductYn", false);
            
            // 필수: productInfoProvidedNotice
            Map<String, Object> productInfoProvidedNotice = new HashMap<>();
            productInfoProvidedNotice.put("productInfoProvidedNoticeType", "ETC");
            Map<String, Object> etc = new HashMap<>();
            etc.put("content", "상품정보제공고시 내용입니다.");
            etc.put("modelName", request.getGroupName() != null ? request.getGroupName() : "일반상품");
            etc.put("itemName", request.getGroupName() != null ? request.getGroupName() : "일반상품");
            etc.put("manufacturer", "제조사명");
            etc.put("afterServiceDirector", "1588-0000");
            productInfoProvidedNotice.put("etc", etc);
            groupProduct.put("productInfoProvidedNotice", productInfoProvidedNotice);
            
            // 필수: afterServiceInfo
            Map<String, Object> afterServiceInfo = new HashMap<>();
            afterServiceInfo.put("possible", true);
            afterServiceInfo.put("afterServiceTelephoneNumber", "1588-0000");
            afterServiceInfo.put("afterServiceGuideContent", "A/S는 구매 후 7일 이내에 연락 주시기 바랍니다.");
            groupProduct.put("afterServiceInfo", afterServiceInfo);
            
            // 공통 상세정보 추출 (첫 번째 specificProduct의 detailContent 사용)
            String commonDetailContent = null;
            if (request.getSpecificProducts() != null && !request.getSpecificProducts().isEmpty()) {
                GroupProductRequest.SpecificProduct firstSp = request.getSpecificProducts().get(0);
                if (firstSp.getDetailContent() != null && !firstSp.getDetailContent().trim().isEmpty()) {
                    commonDetailContent = firstSp.getDetailContent();
                    log.debug("공통 상세정보 추출: 첫 번째 상품의 detailContent 사용");
                }
            }
            
            // 채널 정보 변환 (각 specificProduct에 포함하기 위해)
            Map<String, Object> windowChannel = convertWindowGroupChannelToMap(request.getWindowGroupChannel());
            Map<String, Object> smartstoreChannel = convertSmartstoreGroupChannelToMap(request.getSmartstoreGroupChannel());
            
            // specificProducts 변환 (각 specificProduct에 채널 정보 포함)
            List<Map<String, Object>> specificProductsList = new ArrayList<>();
            for (GroupProductRequest.SpecificProduct sp : request.getSpecificProducts()) {
                Map<String, Object> spMap = convertSpecificProductToMap(sp);
                // 각 specificProduct에 채널 정보 추가
                if (windowChannel != null && !windowChannel.isEmpty()) {
                    spMap.put("windowChannelProduct", windowChannel);
                    log.debug("specificProduct에 windowChannelProduct 추가");
                } else {
                    // smartstoreChannelProduct는 항상 생성됨 (필수 필드 포함)
                    spMap.put("smartstoreChannelProduct", smartstoreChannel);
                    log.debug("specificProduct에 smartstoreChannelProduct 추가");
                }
                specificProductsList.add(spMap);
            }
            groupProduct.put("specificProducts", specificProductsList);
            
            // groupProduct에 commonDetailContent 추가 (필수)
            if (commonDetailContent != null && !commonDetailContent.trim().isEmpty()) {
                groupProduct.put("commonDetailContent", commonDetailContent);
                log.debug("groupProduct에 commonDetailContent 추가");
            }
            
            // groupProduct 안에 smartstoreGroupChannel 추가
            // bbsSeq는 선택 필드이지만, 유효하지 않은 값이면 빈 객체로 보냄
            Map<String, Object> smartstoreGroupChannelInGroup = new HashMap<>();
            // bbsSeq는 공지사항 게시글 일련번호로, 실제 존재하는 값이어야 함
            // 유효하지 않은 값이면 빈 객체로 보내는 것이 안전함
            // 사용자가 명시적으로 bbsSeq를 제공하지 않으면 빈 객체로 전송
            groupProduct.put("smartstoreGroupChannel", smartstoreGroupChannelInGroup);
            log.debug("groupProduct 안에 smartstoreGroupChannel 추가 (빈 객체): {}", smartstoreGroupChannelInGroup);
            
            naverData.put("groupProduct", groupProduct);
            
            // 최상위 레벨에는 채널 정보를 넣지 않음 (각 specificProduct에 포함됨)
            log.info("[최종 확인] 최상위 레벨 키: {}", naverData.keySet());
            
            return naverData;
        }
        
        throw new IllegalArgumentException("그룹상품 등록을 위해서는 groupProduct 객체 또는 필수 필드(leafCategoryId, guideId, specificProducts)가 필요합니다.");
    }
    
    /**
     * SpecificProduct를 Map으로 변환
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSpecificProductToMap(GroupProductRequest.SpecificProduct sp) {
        Map<String, Object> map = new HashMap<>();
        
        // 판매 옵션 정보 (필수)
        // 네이버 API는 standardPurchaseOptions 필드명을 사용
        // 각 옵션은 optionId와 valueName을 포함해야 함
        if (sp.getSaleOptions() != null && !sp.getSaleOptions().isEmpty()) {
            List<Map<String, Object>> standardPurchaseOptions = new ArrayList<>();
            
            for (Map<String, Object> saleOption : sp.getSaleOptions()) {
                Map<String, Object> standardOption = new HashMap<>();
                
                // optionId는 그대로 사용
                if (saleOption.containsKey("optionId")) {
                    standardOption.put("optionId", saleOption.get("optionId"));
                }
                
                // valueName이 있으면 사용하고, 없으면 optionValueId를 기반으로 생성하거나 에러
                if (saleOption.containsKey("valueName")) {
                    standardOption.put("valueName", saleOption.get("valueName"));
                } else if (saleOption.containsKey("optionValueId")) {
                    // optionValueId만 있으면 valueName을 추론할 수 없으므로 에러
                    // 하지만 일단 optionValueId를 valueName으로 사용 (임시)
                    log.warn("valueName이 없어서 optionValueId를 valueName으로 사용합니다. valueName을 명시적으로 제공하는 것을 권장합니다.");
                    standardOption.put("valueName", String.valueOf(saleOption.get("optionValueId")));
                } else {
                    throw new IllegalArgumentException("standardPurchaseOptions에는 optionId와 valueName이 필수입니다.");
                }
                
                standardPurchaseOptions.add(standardOption);
            }
            
            map.put("standardPurchaseOptions", standardPurchaseOptions);
        }
        
        // 상품 정보
        // 그룹상품의 경우 detailContent는 사용하지 않고, commonDetailContent 또는 detailContentTempId 사용
        // detailContentTempId가 있으면 사용 (개별 상세정보)
        if (sp.getDetailContentTempId() != null) {
            map.put("detailContentTempId", sp.getDetailContentTempId());
        }
        // detailContent는 그룹 레벨의 commonDetailContent로 처리됨 (convertToNaverGroupProductFormat에서)
        if (sp.getSalePrice() != null) {
            map.put("salePrice", sp.getSalePrice());
        }
        if (sp.getNormalPrice() != null) {
            map.put("normalPrice", sp.getNormalPrice());
        }
        if (sp.getStockQuantity() != null) {
            map.put("stockQuantity", sp.getStockQuantity());
        }
        if (sp.getImages() != null) {
            map.put("images", sp.getImages());
        } else if (sp.getImageUrls() != null && !sp.getImageUrls().isEmpty()) {
            // 간단한 형식: imageUrls를 images 형식으로 변환
            Map<String, Object> images = new HashMap<>();
            List<Map<String, String>> representImageList = new ArrayList<>();
            for (String imageUrl : sp.getImageUrls()) {
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    Map<String, String> image = new HashMap<>();
                    image.put("url", imageUrl.trim());
                    representImageList.add(image);
                }
            }
            if (!representImageList.isEmpty()) {
                images.put("representativeImage", representImageList.get(0));
                if (representImageList.size() > 1) {
                    images.put("optionalImageList", representImageList.subList(1, Math.min(representImageList.size(), 20)));
                }
            }
            map.put("images", images);
        }
        if (sp.getDeliveryInfo() != null) {
            map.put("deliveryInfo", sp.getDeliveryInfo());
        }
        if (sp.getDetailAttribute() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailAttribute = (Map<String, Object>) sp.getDetailAttribute();
            
            // detailAttribute에 originAreaInfo가 없으면 추가
            if (!detailAttribute.containsKey("originAreaInfo")) {
                Map<String, Object> originAreaInfo = new HashMap<>();
                originAreaInfo.put("originAreaCode", "00"); // API 문서: 00=국산
                originAreaInfo.put("content", "국내산");
                detailAttribute.put("originAreaInfo", originAreaInfo);
            }
            
            map.put("detailAttribute", detailAttribute);
        }
        
        // 필수: originAreaInfo (원산지 정보) - specificProducts의 최상위에 필수
        // 네이버 API 요구사항: specificProducts[0].originAreaInfo가 필수
        if (!map.containsKey("originAreaInfo")) {
            Map<String, Object> originAreaInfo = new HashMap<>();
            originAreaInfo.put("originAreaCode", "00"); // API 문서: 00=국산
            originAreaInfo.put("content", "국내산");
            map.put("originAreaInfo", originAreaInfo);
        }
        
        if (sp.getCustomerBenefit() != null) {
            map.put("customerBenefit", sp.getCustomerBenefit());
        }
        if (sp.getProductLogistics() != null) {
            map.put("productLogistics", sp.getProductLogistics());
        }
        
        return map;
    }
    
    /**
     * SmartstoreGroupChannel을 Map으로 변환
     * 네이버 API 요구사항: smartstoreChannelProduct는 최소한의 필수 필드가 필요
     * bbsSeq는 선택 필드이지만, 있으면 포함하는 것이 안전함
     */
    private Map<String, Object> convertSmartstoreGroupChannelToMap(GroupProductRequest.SmartstoreGroupChannel channel) {
        log.debug("convertSmartstoreGroupChannelToMap 호출 - channel: {}", channel != null ? "존재" : "null");
        
        Map<String, Object> map = new HashMap<>();
        
        // 필수 필드 (네이버 API 요구사항)
            map.put("naverShoppingRegistration", false);
        map.put("channelProductDisplayStatusType", "ON");
        log.debug("필수 필드 추가: naverShoppingRegistration=false, channelProductDisplayStatusType=ON");
        
        // 선택 필드: bbsSeq (공지사항) - 있으면 포함
        // 네이버 API가 smartstoreChannelProduct를 인식하기 위해 bbsSeq가 필요할 수 있음
        if (channel != null && channel.getBbsSeq() != null) {
            map.put("bbsSeq", channel.getBbsSeq());
            log.debug("bbsSeq 추가: {}", channel.getBbsSeq());
        } else {
            log.debug("bbsSeq 없음 (channel이 null이거나 bbsSeq가 null)");
        }
        
        log.debug("변환 완료 - 총 {}개 필드: {}", map.size(), map.keySet());
        return map;
    }
    
    /**
     * WindowGroupChannel을 Map으로 변환
     * API 문서: channelNo (required), best (optional), bbsSeq (optional)
     */
    private Map<String, Object> convertWindowGroupChannelToMap(GroupProductRequest.WindowGroupChannel channel) {
        log.debug("convertWindowGroupChannelToMap 호출 - channel: {}", channel != null ? "존재" : "null");
        
        if (channel == null || channel.getChannelNo() == null) {
            log.debug("windowChannel 변환 불가: channel이 null이거나 channelNo가 null");
            return null;
        }
        
        Map<String, Object> map = new HashMap<>();
        
        // 필수 필드
        map.put("channelNo", channel.getChannelNo());
        log.debug("windowChannel 필수 필드 추가: channelNo={}", channel.getChannelNo());
        
        // 선택 필드: best (기본값 false)
        if (channel.getBest() != null) {
            map.put("best", channel.getBest());
            log.debug("windowChannel best 추가: {}", channel.getBest());
        } else {
            map.put("best", false);
            log.debug("windowChannel best 기본값: false");
        }
        
        // 선택 필드: bbsSeq (공지사항)
        if (channel.getBbsSeq() != null) {
            map.put("bbsSeq", channel.getBbsSeq());
            log.debug("windowChannel bbsSeq 추가: {}", channel.getBbsSeq());
        }
        
        log.debug("windowChannel 변환 완료 - 총 {}개 필드: {}", map.size(), map.keySet());
        return map;
    }
    
    /**
     * 판매 옵션 정보 조회
     * 그룹상품 등록 시 필요한 guideId를 조회하기 위해 사용
     * 
     * @param storeId 스토어 ID
     * @param categoryId 리프 카테고리 ID
     * @return 판매 옵션 정보 (useOptionYn, optionGuides 배열 포함)
     */
    public Map<String, Object> getPurchaseOptionGuides(Long storeId, String categoryId) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("판매 옵션 정보 조회 시작: 스토어={}, categoryId={}", store.getStoreName(), categoryId);
        
        // 네이버 API 호출 (동기 방식)
        Map<String, Object> result = naverCommerceClient.getPurchaseOptionGuides(
                store.getAccessToken(),
                store.getVendorId(),
                categoryId
        ).block(); // 비동기를 동기로 변환
        
        log.info("판매 옵션 정보 조회 완료: 스토어={}, categoryId={}", store.getStoreName(), categoryId);
        
        return result;
    }
    
    /**
     * 전체 카테고리 조회
     * 네이버 스마트스토어의 전체 카테고리 목록을 조회합니다.
     * 
     * @param storeId 스토어 ID
     * @param last 리프 카테고리만 조회 여부 (true: 리프 카테고리만, false: 전체 카테고리)
     * @return 카테고리 목록 (트리 구조)
     */
    public List<Map<String, Object>> getCategories(Long storeId, Boolean last) {
        Store store = storeService.getStoreById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
        
        if (store.getAccessToken() == null || store.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("스토어의 Access Token이 설정되지 않았습니다.");
        }
        
        if (!store.isActive()) {
            throw new IllegalArgumentException("비활성화된 스토어입니다.");
        }
        
        log.info("카테고리 정보 조회 시작: 스토어={}, last={}", store.getStoreName(), last);
        
        // 네이버 API 호출 (동기 방식)
        List<Map<String, Object>> result = naverCommerceClient.getCategories(
                store.getAccessToken(),
                last
        ).block(); // 비동기를 동기로 변환
        
        log.info("카테고리 정보 조회 완료: 스토어={}, 카테고리 개수={}", 
                store.getStoreName(), result != null ? result.size() : 0);
        
        return result;
    }
    
    /**
     * 전체 카테고리 조회 (현재 스토어 사용)
     * 등록된 스토어의 카테고리 목록을 조회합니다.
     * 
     * @param last 리프 카테고리만 조회 여부 (true: 리프 카테고리만, false: 전체 카테고리)
     * @return 카테고리 목록 (트리 구조)
     */
    public List<Map<String, Object>> getCategories(Boolean last) {
        Store store = getCurrentStore()
                .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
        
        return getCategories(store.getId(), last);
    }
    
    /**
     * 카테고리별 원산지 정보를 자동 생성합니다.
     * 카테고리와 원산지에 따라 필요한 필드를 자동으로 추가합니다.
     * 
     * @param originArea 원산지 (예: "국내산", "중국", "일본" 등)
     * @param categoryPath 카테고리 경로 (예: "식품 > 과자/간식 > 과자")
     * @return 원산지 정보 Map
     */
    private Map<String, Object> createOriginAreaInfo(String originArea, String categoryPath) {
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
            boolean isForeignOrigin = originAreaCode.startsWith("02");
            
            // 카테고리별 특수 필드 처리 (해산물 여부 판단)
            boolean isMarineCategory = false;
            if (categoryPath != null && !categoryPath.trim().isEmpty()) {
                String trimmedCategoryPath = categoryPath.trim();
                
                // 숫자 ID인지 확인 (카테고리 ID는 보통 숫자)
                boolean isNumericCategoryId = trimmedCategoryPath.matches("^\\d+$");
                
                if (!isNumericCategoryId) {
                    // 한글 카테고리 경로인 경우에만 해산물 여부 판단
                    isMarineCategory = trimmedCategoryPath.contains("해산물") || 
                                      trimmedCategoryPath.contains("수산물") ||
                                      trimmedCategoryPath.contains("어류") ||
                                      trimmedCategoryPath.contains("조개류");
                    
                    log.info("카테고리 경로 분석: categoryPath={}, isMarineCategory={}", 
                            trimmedCategoryPath, isMarineCategory);
                } else {
                    // 숫자 ID인 경우 해산물 여부를 알 수 없으므로 안전하게 false로 설정
                    // (해산물이 아닌 것으로 간주하여 oceanName/oceanType 제거)
                    log.info("카테고리 ID가 숫자입니다. 해산물 여부를 판단할 수 없어 안전하게 false로 설정: categoryId={}", 
                            trimmedCategoryPath);
                    isMarineCategory = false;
                }
            } else {
                // 카테고리 경로가 없으면 해산물이 아닌 것으로 간주
                log.info("카테고리 경로가 없습니다. 해산물이 아닌 것으로 간주");
                isMarineCategory = false;
            }
            
            // 해외 원산지인 경우 수입사 필수 필드 채우기
            // API 문서: originAreaCode=02 (수입산)일 때 importer 필드가 필수
            // 주의: API 문서에는 importCountry 필드가 없지만, 에러 메시지에서 요구할 수 있음
            // 일단 API 문서대로 importer만 설정하고, 에러가 발생하면 추가 조사 필요
            if (isForeignOrigin) {
                // 수입사: 필수 필드 (API 문서 명시)
                // 기본값 설정 (추후 엑셀/설정값으로 교체 가능)
                originAreaInfo.put("importer", "수입사명"); // TODO: 엑셀/설정/Store에서 가져오도록 확장
                
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
            originAreaInfo.put("originAreaCode", "00"); // API 문서: 00=국산
            originAreaInfo.put("content", "국내산");
        }
        
        // 최종 확인: originAreaCode가 반드시 포함되어 있어야 함
        if (!originAreaInfo.containsKey("originAreaCode")) {
            log.error("createOriginAreaInfo 반환 시 originAreaCode가 없습니다! originAreaInfo={}", originAreaInfo);
            // 기본값 설정: 국산
            originAreaInfo.put("originAreaCode", "00");
        }
        
        log.debug("createOriginAreaInfo 반환: originAreaInfo={}", originAreaInfo);
        return originAreaInfo;
    }
    
    /**
     * 원산지 이름에서 국가명 추출
     * 예: "수입산:아시아>베트남" -> "베트남"
     * 
     * @param name 원산지 이름
     * @return 국가명
     */
    private String extractCountryName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        // ">" 기호로 분리하여 마지막 부분 추출
        if (name.contains(">")) {
            String[] parts = name.split(">");
            if (parts.length > 0) {
                return parts[parts.length - 1].trim();
            }
        }
        
        // ":" 기호로 분리하여 마지막 부분 추출
        if (name.contains(":")) {
            String[] parts = name.split(":");
            if (parts.length > 1) {
                String lastPart = parts[parts.length - 1].trim();
                // ">" 기호가 있으면 다시 분리
                if (lastPart.contains(">")) {
                    String[] subParts = lastPart.split(">");
                    return subParts[subParts.length - 1].trim();
                }
                return lastPart;
            }
        }
        
        return name.trim();
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
    private String determineOriginAreaCode(String originArea) {
        if (originArea == null || originArea.trim().isEmpty()) {
            return "00"; // 기본값: 국산
        }
        
        String trimmed = originArea.trim();
        
        // 국내산 판단 (명시적으로 국내산인 경우만)
        if (trimmed.contains("국내") || trimmed.contains("한국") || trimmed.contains("국산")) {
            return "00"; // 국산
        }
        
        // 이하는 전부 해외산(수입산) 취급
        // API 문서에 따르면 수입산은 "02" 사용
        log.debug("해외 원산지로 판단: {}. originAreaCode=02(수입산) 사용", trimmed);
        return "02"; // 수입산
    }
}

