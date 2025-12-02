package com.example.auto.client;

import com.example.auto.config.NaverCommerceProperties;
import com.example.auto.util.SignatureUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.BodyInserters.FormInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 네이버 커머스 API 클라이언트
 * <p>
 * API 문서: https://api.commerce.naver.com/partner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverCommerceClient {

    // SmartStore 커머스 API는 /external prefix 사용
    private static final String EXTERNAL_PREFIX = "/external";

    private final WebClient webClient;
    private final NaverCommerceProperties properties;

    /**
     * 네이버 커머스 API 인증 토큰 발급 (전자서명 기반)
     *
     * @param type      인증 토큰 발급 타입 (SELF 또는 SELLER)
     * @param accountId 판매자 ID (type이 SELLER인 경우 필수, SELF에서는 사용 안 함)
     * @return Access Token 정보
     */
    public Mono<Map<String, Object>> getAccessToken(String type, String accountId) {
        long timestamp = System.currentTimeMillis();

        // type 기본값: SELF (내스토어 애플리케이션 기준)
        String finalType = (type == null || type.isBlank()) ? "SELF" : type.toUpperCase();

        // SELF 타입일 때는 account_id를 절대 보내지 않음
        if ("SELF".equalsIgnoreCase(finalType)) {
            accountId = null;
        }

        // 전자서명 생성
        String clientSecretSign = SignatureUtil.generateSignature(
                properties.getClientId(),
                timestamp,
                properties.getClientSecret()
        );

        // 요청 본문 생성 (application/x-www-form-urlencoded)
        FormInserter<String> formData = BodyInserters
                .fromFormData("client_id", properties.getClientId())
                .with("timestamp", String.valueOf(timestamp))
                .with("grant_type", "client_credentials")
                .with("client_secret_sign", clientSecretSign)
                .with("type", finalType);

        // SELLER 타입일 때만 account_id 전송 (솔루션 애플리케이션용)
        if (accountId != null && !accountId.isEmpty()) {
            formData = formData.with("account_id", accountId);
        }

        String fullUrl = properties.getApiBaseUrl() + EXTERNAL_PREFIX + "/v1/oauth2/token";

        // 요청 로깅
        log.info("토큰 발급 요청: URL={}, type={}, accountId={}, timestamp={}",
                fullUrl, finalType, accountId, timestamp);
        log.info("요청 파라미터: client_id={}, timestamp={}, grant_type=client_credentials, type={}, account_id={}",
                properties.getClientId(), timestamp, finalType, accountId != null ? accountId : "null");
        log.debug("전자서명 생성 정보: clientId={}, timestamp={}, clientSecretSign(앞 20자)={}",
                properties.getClientId(),
                timestamp,
                clientSecretSign.substring(0, Math.min(20, clientSecretSign.length())) + "...");

        return webClient.post()
                .uri(EXTERNAL_PREFIX + "/v1/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(formData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("Access Token 발급 성공: {}", response))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();

                        log.error("Access Token 발급 실패: status={}, URL={}",
                                ex.getStatusCode(),
                                ex.getRequest() != null ? ex.getRequest().getURI() : "unknown");
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");

                        // JSON 응답 파싱 시도 (400 에러의 경우 invalidInputs 확인)
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                Map<String, Object> errorResponse = objectMapper.readValue(
                                        responseBody,
                                        new TypeReference<Map<String, Object>>() {
                                        }
                                );

                                log.error("에러 코드: {}", errorResponse.get("code"));
                                log.error("에러 메시지: {}", errorResponse.get("message"));

                                if (errorResponse.containsKey("invalidInputs")) {
                                    Object invalidInputs = errorResponse.get("invalidInputs");
                                    log.error("유효하지 않은 입력 필드: {}", invalidInputs);
                                }
                            } catch (Exception parseError) {
                                log.debug("응답 본문 파싱 실패 (일반 텍스트일 수 있음)", parseError);
                            }
                        }
                    } else {
                        log.error("Access Token 발급 실패", error);
                    }
                });
    }

    /**
     * Access Token 갱신
     * (일반 OAuth 패턴용. 커머스 전용에선 보통 client_credentials 재호출)
     */
    public Mono<Map<String, Object>> refreshAccessToken(String refreshToken) {
        FormInserter<String> formData = BodyInserters
                .fromFormData("grant_type", "refresh_token")
                .with("client_id", properties.getClientId())
                .with("client_secret", properties.getClientSecret())
                .with("refresh_token", refreshToken);

        return webClient.post()
                .uri(EXTERNAL_PREFIX + "/v1/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(formData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("Access Token 갱신 성공"))
                .doOnError(error -> log.error("Access Token 갱신 실패", error));
    }

    /**
     * 사용자 정보 조회 (vendorId 포함 – /external/v2/channels)
     */
    public Mono<Map<String, Object>> getUserInfo(String accessToken) {
        return webClient.get()
                .uri(EXTERNAL_PREFIX + "/v2/channels")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("사용자 정보 조회 성공"))
                .doOnError(error -> log.error("사용자 정보 조회 실패", error));
    }

    /**
     * 상품 목록 조회 (채널 상품 조회)
     */
    public Mono<Map<String, Object>> getProducts(String accessToken, String vendorId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/channel-products")
                        .queryParam("vendorId", vendorId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("상품 목록 조회 성공"))
                .doOnError(error -> log.error("상품 목록 조회 실패", error));
    }

    /**
     * 판매 옵션 정보 조회
     * 그룹상품 등록 시 필요한 guideId를 조회하기 위해 사용
     *
     * @param accessToken Access Token
     * @param vendorId    판매자 ID
     * @param categoryId  리프 카테고리 ID
     * @return 판매 옵션 정보 (useOptionYn, optionGuides 배열 포함)
     */
    public Mono<Map<String, Object>> getPurchaseOptionGuides(String accessToken,
                                                             String vendorId,
                                                             String categoryId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/standard-purchase-option-guides")
                        .queryParam("vendorId", vendorId)
                        .queryParam("categoryId", categoryId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> {
                    log.info("판매 옵션 정보 조회 성공: categoryId={}", categoryId);
                    if (response.containsKey("useOptionYn")) {
                        Boolean useOptionYn = (Boolean) response.get("useOptionYn");
                        log.info("판매 옵션 사용 가능 여부: {}", useOptionYn);
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("판매 옵션 정보 조회 실패: status={}, categoryId={}",
                                ex.getStatusCode(), categoryId);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("판매 옵션 정보 조회 실패: categoryId={}", categoryId, error);
                    }
                });
    }

    /**
     * 상품 등록
     */
    public Mono<Map<String, Object>> createProduct(String accessToken,
                                                   String vendorId,
                                                   Map<String, Object> productData) {
        String url = properties.getApiBaseUrl() + EXTERNAL_PREFIX + "/v2/products";

        // 요청 본문 로깅 (디버깅용)
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(productData);
            log.info("상품 등록 요청: URL={}, vendorId={}", url, vendorId);
            log.info("요청 본문 (이미지 정보 포함): {}", requestBody);
        } catch (Exception e) {
            log.debug("요청 본문 로깅 실패", e);
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/products")
                        .queryParam("vendorId", vendorId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(productData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("상품 등록 성공: {}", response))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 등록 실패: status={}, URL={}", ex.getStatusCode(), url);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");

                        // 에러 응답 파싱 시도
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                Map<String, Object> errorResponse = objectMapper.readValue(
                                        responseBody,
                                        new TypeReference<Map<String, Object>>() {
                                        }
                                );

                                log.error("에러 코드: {}", errorResponse.get("code"));
                                log.error("에러 메시지: {}", errorResponse.get("message"));

                                if (errorResponse.containsKey("invalidInputs")) {
                                    Object invalidInputs = errorResponse.get("invalidInputs");
                                    log.error("유효하지 않은 입력 필드: {}", invalidInputs);
                                }

                                if (errorResponse.containsKey("timestamp")) {
                                    log.error("에러 발생 일시: {}", errorResponse.get("timestamp"));
                                }
                            } catch (Exception parseError) {
                                log.debug("에러 응답 본문 JSON 파싱 실패 (일반 텍스트일 수 있음)", parseError);
                                log.error("에러 응답 (텍스트): {}", responseBody);
                            }
                        }
                    } else {
                        log.error("상품 등록 실패", error);
                    }
                });
    }

    /**
     * 그룹상품 등록
     * 여러 개의 상품을 하나의 그룹으로 묶어 등록
     *
     * @param accessToken      Access Token
     * @param vendorId         판매자 ID
     * @param groupProductData 그룹상품 데이터 (groupProduct 객체 포함)
     * @return 등록된 그룹상품 정보
     */
    public Mono<Map<String, Object>> createGroupProduct(String accessToken,
                                                        String vendorId,
                                                        Map<String, Object> groupProductData) {
        String url = properties.getApiBaseUrl() + EXTERNAL_PREFIX + "/v2/standard-group-products";

        // 요청 본문 로깅 (디버깅용)
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(groupProductData);
            log.info("그룹상품 등록 요청: URL={}, vendorId={}", url, vendorId);
            log.info("요청 본문: {}", requestBody);
        } catch (Exception e) {
            log.debug("요청 본문 로깅 실패", e);
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/standard-group-products")
                        .queryParam("vendorId", vendorId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(groupProductData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("그룹상품 등록 성공: {}", response))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("그룹상품 등록 실패: status={}, URL={}", ex.getStatusCode(), url);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");

                        // 에러 응답 파싱 시도
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                Map<String, Object> errorResponse = objectMapper.readValue(
                                        responseBody,
                                        new TypeReference<Map<String, Object>>() {
                                        }
                                );

                                log.error("에러 코드: {}", errorResponse.get("code"));
                                log.error("에러 메시지: {}", errorResponse.get("message"));

                                if (errorResponse.containsKey("invalidInputs")) {
                                    Object invalidInputs = errorResponse.get("invalidInputs");
                                    log.error("유효하지 않은 입력 필드: {}", invalidInputs);
                                }

                                if (errorResponse.containsKey("timestamp")) {
                                    log.error("에러 발생 일시: {}", errorResponse.get("timestamp"));
                                }
                            } catch (Exception parseError) {
                                log.debug("에러 응답 본문 JSON 파싱 실패 (일반 텍스트일 수 있음)", parseError);
                                log.error("에러 응답 (텍스트): {}", responseBody);
                            }
                        }
                    } else {
                        log.error("그룹상품 등록 실패", error);
                    }
                });
    }

    /**
     * 그룹상품 요청 결과 조회
     * 그룹상품 등록/수정 API의 처리 상태를 조회합니다.
     *
     * @param accessToken Access Token
     * @param vendorId    판매자 ID
     * @param type        요청 타입 (CREATE: 그룹상품 등록, UPDATE: 그룹상품 수정)
     * @param requestId   조회할 요청 ID
     * @return 그룹상품 요청 결과 (progress, state, requestId, groupProductNo 등)
     */
    public Mono<Map<String, Object>> getGroupProductStatus(String accessToken,
                                                           String vendorId,
                                                           String type,
                                                           String requestId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/standard-group-products/status")
                        .queryParam("vendorId", vendorId)
                        .queryParam("type", type)
                        .queryParam("requestId", requestId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> {
                    log.info("그룹상품 요청 결과 조회 성공: type={}, requestId={}", type, requestId);
                    if (response.containsKey("progress")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> progress = (Map<String, Object>) response.get("progress");
                        if (progress != null && progress.containsKey("state")) {
                            log.info("처리 상태: {}", progress.get("state"));
                            if (progress.containsKey("progress")) {
                                log.info("진행률: {}%", progress.get("progress"));
                            }
                        }
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("그룹상품 요청 결과 조회 실패: status={}, type={}, requestId={}",
                                ex.getStatusCode(), type, requestId);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("그룹상품 요청 결과 조회 실패: type={}, requestId={}", type, requestId, error);
                    }
                });
    }

    /**
     * 브랜드 조회
     * 전체 브랜드 목록을 조회합니다.
     *
     * @param accessToken Access Token
     * @param vendorId    판매자 ID
     * @return 브랜드 목록 (배열)
     */
    public Mono<List<Map<String, Object>>> getProductBrands(String accessToken, String vendorId) {
        // 브랜드 조회는 vendorId가 필요할 수 있음
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v1/product-brands")
                        .queryParam("vendorId", vendorId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .doOnSuccess(response -> log.info("브랜드 조회 성공: {}개 브랜드", response != null ? response.size() : 0))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("브랜드 조회 실패: status={}, vendorId={}", ex.getStatusCode(), vendorId);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("브랜드 조회 실패", error);
                    }
                });
    }

    /**
     * 전체 사이즈 타입 조회
     * 전체 사이즈 타입 목록을 조회합니다.
     *
     * @param accessToken Access Token
     * @param vendorId    판매자 ID (필요한 경우 사용)
     * @return 사이즈 타입 목록 (배열)
     */
    public Mono<List<Map<String, Object>>> getProductSizes(String accessToken, String vendorId) {
        // vendorId는 query parameter로 보내지 않음 (네이버 API가 요구하지 않음)
        return webClient.get()
                .uri(EXTERNAL_PREFIX + "/v1/product-sizes")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                })
                .doOnSuccess(response -> log.info("사이즈 타입 조회 성공: {}개 사이즈 타입", response != null ? response.size() : 0))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("사이즈 타입 조회 실패: status={}", ex.getStatusCode());
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("사이즈 타입 조회 실패", error);
                    }
                });
    }

    /**
     * 상품 목록 조회
     * 검색 조건에 따라 상품 목록을 조회합니다.
     *
     * @param accessToken   Access Token
     * @param vendorId      판매자 ID
     * @param searchRequest 검색 요청 데이터
     * @return 상품 목록
     */
    public Mono<Map<String, Object>> searchProducts(String accessToken,
                                                    String vendorId,
                                                    Map<String, Object> searchRequest) {
        String url = properties.getApiBaseUrl() + EXTERNAL_PREFIX + "/v1/products/search";

        // 요청 본문 로깅 (디버깅용)
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(searchRequest);
            log.info("상품 목록 조회 요청: URL={}, vendorId={}", url, vendorId);
            log.debug("요청 본문: {}", requestBody);
        } catch (Exception e) {
            log.debug("요청 본문 로깅 실패", e);
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v1/products/search")
                        .queryParam("vendorId", vendorId)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("상품 목록 조회 성공"))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("상품 목록 조회 실패: status={}, URL={}", ex.getStatusCode(), url);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");

                        // 에러 응답 파싱 시도
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                Map<String, Object> errorResponse = objectMapper.readValue(
                                        responseBody,
                                        new TypeReference<Map<String, Object>>() {
                                        }
                                );

                                log.error("에러 코드: {}", errorResponse.get("code"));
                                log.error("에러 메시지: {}", errorResponse.get("message"));

                                if (errorResponse.containsKey("invalidInputs")) {
                                    Object invalidInputs = errorResponse.get("invalidInputs");
                                    log.error("유효하지 않은 입력 필드: {}", invalidInputs);
                                }
                            } catch (Exception parseError) {
                                log.debug("에러 응답 본문 JSON 파싱 실패", parseError);
                            }
                        }
                    } else {
                        log.error("상품 목록 조회 실패", error);
                    }
                });
    }

    /**
     * 주문 목록 조회
     */
    public Mono<Map<String, Object>> getOrders(String accessToken,
                                               String vendorId,
                                               LocalDateTime startDate,
                                               LocalDateTime endDate) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/orders")
                        .queryParam("vendorId", vendorId)
                        .queryParam("startDate", startDate.toString())
                        .queryParam("endDate", endDate.toString())
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("주문 목록 조회 성공"))
                .doOnError(error -> log.error("주문 목록 조회 실패", error));
    }

    /**
     * 원상품 조회
     * 원상품 번호로 원상품 정보를 조회합니다.
     *
     * @param accessToken     Access Token
     * @param vendorId        판매자 ID
     * @param originProductNo 원상품 번호
     * @return 원상품 정보
     */
    public Mono<Map<String, Object>> getOriginProduct(String accessToken,
                                                      String vendorId,
                                                      Long originProductNo) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/products/origin-products/{originProductNo}")
                        .queryParam("vendorId", vendorId)
                        .build(originProductNo))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("원상품 조회 성공: originProductNo={}", originProductNo))
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("원상품 조회 실패: status={}, originProductNo={}",
                                ex.getStatusCode(), originProductNo);
                        log.error("응답 본문: {}", responseBody != null ? responseBody : "null");
                    } else {
                        log.error("원상품 조회 실패: originProductNo={}", originProductNo, error);
                    }
                });
    }

    /**
     * 재고 조회
     */
    public Mono<Map<String, Object>> getInventory(String accessToken,
                                                  String vendorId,
                                                  String productId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/products/{productId}/inventory")
                        .queryParam("vendorId", vendorId)
                        .build(productId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("재고 조회 성공"))
                .doOnError(error -> log.error("재고 조회 실패", error));
    }

    /**
     * 재고 수정
     */
    public Mono<Map<String, Object>> updateInventory(String accessToken,
                                                     String vendorId,
                                                     String productId,
                                                     Integer quantity) {
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path(EXTERNAL_PREFIX + "/v2/products/{productId}/inventory")
                        .queryParam("vendorId", vendorId)
                        .build(productId))
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("quantity", quantity))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .doOnSuccess(response -> log.info("재고 수정 성공"))
                .doOnError(error -> log.error("재고 수정 실패", error));
    }

    /**
     * 상품 이미지 업로드 (상품 이미지 다건 등록 API)
     * 외부 이미지 URL에서 이미지를 다운로드한 후 네이버 서버에 업로드합니다.
     * <p>
     * 엔드포인트: POST /v1/product-images/upload
     * 방식: multipart/form-data
     * 필드: imageFiles (binary[], 최대 10개)
     *
     * @param accessToken Access Token
     * @param vendorId    판매자 ID
     * @param imageUrl    업로드할 이미지 URL (외부 URL)
     * @return 업로드된 이미지 정보 (네이버 서버의 URL 포함)
     */
    public Mono<Map<String, Object>> uploadProductImage(String accessToken,
                                                        String vendorId,
                                                        String imageUrl) {
        log.info("이미지 업로드 시작: vendorId={}, imageUrl={}", vendorId, imageUrl);

        // 1. 외부 이미지 URL에서 이미지 다운로드
        return webClient.get()
                .uri(imageUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(imageBytes -> {
                    log.info("이미지 다운로드 완료: {} bytes", imageBytes.length);

                    // 2. multipart/form-data로 이미지 업로드
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("imageFiles", imageBytes)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "form-data; name=\"imageFiles\"; filename=\"image.jpg\"")
                            .contentType(MediaType.IMAGE_JPEG); // 이미지 타입은 실제 파일에 맞게 조정 필요

                    // 네이버 API는 /external prefix를 사용할 수 있음
                    // 가능한 엔드포인트들 시도
                    return webClient.post()
                            .uri(uriBuilder -> uriBuilder
                                    .path(EXTERNAL_PREFIX + "/v1/product-images/upload")
                                    .queryParam("vendorId", vendorId)
                                    .build())
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(BodyInserters.fromMultipartData(builder.build()))
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                            })
                            .doOnSuccess(response -> log.info("이미지 업로드 성공: {}", response))
                            .onErrorResume(WebClientResponseException.NotFound.class, notFoundError -> {
                                // 404인 경우 /external 없이 시도
                                log.warn("첫 번째 엔드포인트 404, /external 없이 시도: /v1/product-images/upload");
                                return webClient.post()
                                        .uri(uriBuilder -> uriBuilder
                                                .path("/v1/product-images/upload")
                                                .queryParam("vendorId", vendorId)
                                                .build())
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.MULTIPART_FORM_DATA)
                                        .body(BodyInserters.fromMultipartData(builder.build()))
                                        .retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                                        })
                                        .doOnSuccess(response -> log.info("이미지 업로드 성공 (두 번째 엔드포인트): {}", response));
                            })
                            .doOnError(error -> {
                                if (error instanceof WebClientResponseException ex) {
                                    String responseBody = ex.getResponseBodyAsString();
                                    log.error("이미지 업로드 실패: status={}, URL={}", ex.getStatusCode(),
                                            ex.getRequest() != null ? ex.getRequest().getURI() : "unknown");
                                    log.error("응답 본문: {}", responseBody != null ? responseBody : "null");

                                    // JSON 응답 파싱 시도
                                    if (responseBody != null && !responseBody.isEmpty() && responseBody.startsWith("{")) {
                                        try {
                                            ObjectMapper objectMapper = new ObjectMapper();
                                            Map<String, Object> errorResponse = objectMapper.readValue(
                                                    responseBody,
                                                    new TypeReference<Map<String, Object>>() {
                                                    }
                                            );
                                            log.error("에러 코드: {}", errorResponse.get("code"));
                                            log.error("에러 메시지: {}", errorResponse.get("message"));
                                        } catch (Exception parseError) {
                                            log.debug("에러 응답 파싱 실패", parseError);
                                        }
                                    }
                                } else {
                                    log.error("이미지 업로드 실패", error);
                                }
                            });
                })
                .doOnError(error -> {
                    log.error("이미지 다운로드 실패: {}", imageUrl, error);
                });
    }
}
