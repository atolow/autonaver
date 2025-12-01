package com.example.auto.service;

import com.example.auto.client.NaverCommerceClient;
import com.example.auto.domain.Store;
import com.example.auto.dto.GroupProductRequest;
import com.example.auto.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    
    /**
     * 현재 등록된 스토어 조회 (독립 실행형용)
     */
    public java.util.Optional<com.example.auto.domain.Store> getCurrentStore() {
        return storeService.getCurrentStore();
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
        
        // 이미지가 있으면 먼저 네이버 서버에 업로드
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            log.info("이미지 업로드 시작: {}개 이미지", request.getImages().size());
            List<String> uploadedImageUrls = new ArrayList<>();
            
            for (String imageUrl : request.getImages()) {
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
                            log.info("이미지 업로드 응답: {}", uploadResponse);
                            
                            // 네이버 API 응답 구조에 따라 URL 추출 시도
                            // 가능한 구조:
                            // 1. {"url": "..."}
                            // 2. {"imageUrl": "..."}
                            // 3. {"data": {"url": "..."}}
                            // 4. {"images": [{"url": "..."}]}
                            // 5. {"imageUrls": ["..."]}
                            
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
            request.setImages(uploadedImageUrls);
            log.info("이미지 업로드 완료: {}개 이미지", uploadedImageUrls.size());
        }
        
        // 사용자 친화적인 요청을 네이버 API 형식으로 변환
        Map<String, Object> naverProductData = convertToNaverFormat(request);
        
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
     * 사용자 친화적인 요청을 네이버 API 형식으로 변환
     * 네이버 API 형식과 간단한 형식 모두 지원
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToNaverFormat(ProductRequest request) {
        Map<String, Object> naverData = new HashMap<>();
        
        // 네이버 API 형식으로 이미 들어온 경우 그대로 사용
        if (request.getOriginProduct() != null) {
            Map<String, Object> originProductMap = convertOriginProductToMap(request.getOriginProduct());
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
        
        // 필수 필드
        originProduct.put("statusType", request.getStatusType() != null ? request.getStatusType() : "SALE");
        originProduct.put("saleType", request.getSaleType() != null ? request.getSaleType() : "NEW");
        originProduct.put("leafCategoryId", request.getLeafCategoryId());
        originProduct.put("name", request.getName());
        originProduct.put("detailContent", request.getDetailContent());
        originProduct.put("salePrice", request.getSalePrice().longValue());
        originProduct.put("stockQuantity", request.getStockQuantity());
        
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
                deliveryFee.put("baseFee", request.getDeliveryInfo().getDeliveryFee());
                deliveryFee.put("freeConditionalAmount", 
                        request.getDeliveryInfo().getFreeDeliveryMinAmount() != null 
                        ? request.getDeliveryInfo().getFreeDeliveryMinAmount() 
                        : 0);
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
        etc.put("modelName", request.getName() != null ? request.getName() : "일반상품"); // 모델명 (필수)
        etc.put("itemName", request.getName() != null ? request.getName() : "일반상품"); // 품목명 (필수)
        etc.put("manufacturer", "제조사명"); // 제조사 (필수)
        etc.put("afterServiceDirector", "1588-0000"); // A/S 책임자 또는 소비자 상담 관련 전화번호 (필수)
        productInfoProvidedNotice.put("etc", etc);
        detailAttribute.put("productInfoProvidedNotice", productInfoProvidedNotice);
        
        // 원산지 정보 (필수)
        Map<String, Object> originAreaInfo = new HashMap<>();
        originAreaInfo.put("originAreaCode", "04"); // 국내 (04: 국내, 기타: 해외)
        originAreaInfo.put("content", "국내산"); // 원산지 내용
        detailAttribute.put("originAreaInfo", originAreaInfo);
        
        originProduct.put("detailAttribute", detailAttribute);
        
        // 스마트스토어 채널상품 정보
        Map<String, Object> smartstoreChannelProduct = new HashMap<>();
        smartstoreChannelProduct.put("naverShoppingRegistration", 
                request.getNaverShoppingRegistration() != null ? request.getNaverShoppingRegistration() : false);
        smartstoreChannelProduct.put("channelProductDisplayStatusType", 
                request.getChannelProductDisplayStatusType() != null ? request.getChannelProductDisplayStatusType() : "ON");
        
        // 최종 구조
        naverData.put("originProduct", originProduct);
        naverData.put("smartstoreChannelProduct", smartstoreChannelProduct);
        
        return naverData;
    }
    
    /**
     * OriginProduct 객체를 Map으로 변환
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOriginProductToMap(ProductRequest.OriginProduct originProduct) {
        Map<String, Object> map = new HashMap<>();
        
        if (originProduct.getStatusType() != null) {
            map.put("statusType", originProduct.getStatusType());
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
            
            // deliveryFee가 PAID일 때 deliveryFeePayType 필수 확인 및 추가
            if (deliveryInfoMap.containsKey("deliveryFee")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> deliveryFee = (Map<String, Object>) deliveryInfoMap.get("deliveryFee");
                if (deliveryFee != null && "PAID".equals(deliveryFee.get("deliveryFeeType"))) {
                    if (!deliveryFee.containsKey("deliveryFeePayType")) {
                        deliveryFee.put("deliveryFeePayType", "PREPAID"); // 선결제 (필수)
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
            
            if (!detailAttributeMap.containsKey("originAreaInfo")) {
                Map<String, Object> originAreaInfo = new HashMap<>();
                originAreaInfo.put("originAreaCode", "04");
                originAreaInfo.put("content", "국내산");
                detailAttributeMap.put("originAreaInfo", originAreaInfo);
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
            
            Map<String, Object> originAreaInfo = new HashMap<>();
            originAreaInfo.put("originAreaCode", "04");
            originAreaInfo.put("content", "국내산");
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
                originAreaInfo.put("originAreaCode", "04"); // 국내 (04: 국내)
                originAreaInfo.put("content", "국내산");
                detailAttribute.put("originAreaInfo", originAreaInfo);
            }
            
            map.put("detailAttribute", detailAttribute);
        }
        
        // 필수: originAreaInfo (원산지 정보) - specificProducts의 최상위에 필수
        // 네이버 API 요구사항: specificProducts[0].originAreaInfo가 필수
        if (!map.containsKey("originAreaInfo")) {
            Map<String, Object> originAreaInfo = new HashMap<>();
            originAreaInfo.put("originAreaCode", "04"); // 국내 (04: 국내)
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
}

