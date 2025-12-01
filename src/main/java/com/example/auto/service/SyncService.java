package com.example.auto.service;

import com.example.auto.client.NaverCommerceClient;
import com.example.auto.domain.Order;
import com.example.auto.domain.Product;
import com.example.auto.domain.Store;
import com.example.auto.repository.OrderRepository;
import com.example.auto.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 네이버 스마트스토어 동기화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SyncService {
    
    private final NaverCommerceClient naverCommerceClient;
    private final StoreService storeService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    
    /**
     * 상품 목록 동기화
     */
    public void syncProducts(Store store) {
        log.info("상품 동기화 시작: {}", store.getStoreName());
        
        try {
            naverCommerceClient.getProducts(store.getAccessToken(), store.getVendorId())
                    .subscribe(response -> {
                        // TODO: API 응답을 파싱하여 Product 엔티티로 변환 후 저장
                        log.info("상품 동기화 완료: {}", store.getStoreName());
                    }, error -> {
                        log.error("상품 동기화 실패: {}", store.getStoreName(), error);
                    });
        } catch (Exception e) {
            log.error("상품 동기화 중 오류 발생: {}", store.getStoreName(), e);
        }
    }
    
    /**
     * 주문 목록 동기화
     */
    public void syncOrders(Store store, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("주문 동기화 시작: {} ({} ~ {})", store.getStoreName(), startDate, endDate);
        
        try {
            naverCommerceClient.getOrders(store.getAccessToken(), store.getVendorId(), startDate, endDate)
                    .subscribe(response -> {
                        // TODO: API 응답을 파싱하여 Order 엔티티로 변환 후 저장
                        log.info("주문 동기화 완료: {}", store.getStoreName());
                    }, error -> {
                        log.error("주문 동기화 실패: {}", store.getStoreName(), error);
                    });
        } catch (Exception e) {
            log.error("주문 동기화 중 오류 발생: {}", store.getStoreName(), e);
        }
    }
    
    /**
     * 재고 동기화
     */
    public void syncInventory(Store store, Product product) {
        log.info("재고 동기화 시작: {} - {}", store.getStoreName(), product.getProductName());
        
        try {
            naverCommerceClient.getInventory(store.getAccessToken(), store.getVendorId(), product.getProductId())
                    .subscribe(response -> {
                        // TODO: API 응답을 파싱하여 재고 수량 업데이트
                        log.info("재고 동기화 완료: {} - {}", store.getStoreName(), product.getProductName());
                    }, error -> {
                        log.error("재고 동기화 실패: {} - {}", store.getStoreName(), product.getProductName(), error);
                    });
        } catch (Exception e) {
            log.error("재고 동기화 중 오류 발생: {} - {}", store.getStoreName(), product.getProductName(), e);
        }
    }
    
    /**
     * 모든 활성 스토어의 상품 동기화
     */
    public void syncAllProducts() {
        List<Store> activeStores = storeService.getActiveStores();
        for (Store store : activeStores) {
            syncProducts(store);
        }
    }
    
    /**
     * 모든 활성 스토어의 주문 동기화 (최근 7일)
     */
    public void syncAllOrders() {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(7);
        
        List<Store> activeStores = storeService.getActiveStores();
        for (Store store : activeStores) {
            syncOrders(store, startDate, endDate);
        }
    }
}

