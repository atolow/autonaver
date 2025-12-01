package com.example.auto.controller;

import com.example.auto.domain.Store;
import com.example.auto.service.StoreService;
import com.example.auto.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 동기화 REST API
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {
    
    private final SyncService syncService;
    private final StoreService storeService;
    
    /**
     * 상품 동기화 (독립 실행형: 현재 스토어만 동기화)
     */
    @PostMapping("/products")
    public ResponseEntity<String> syncProducts() {
        return storeService.getCurrentStore()
                .map(store -> {
                    syncService.syncProducts(store);
                    return ResponseEntity.ok("상품 동기화가 시작되었습니다.");
                })
                .orElse(ResponseEntity.badRequest()
                        .body("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
    }
    
    /**
     * 주문 동기화 (독립 실행형: 현재 스토어만 동기화)
     */
    @PostMapping("/orders")
    public ResponseEntity<String> syncOrders(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        
        return storeService.getCurrentStore()
                .map(store -> {
                    LocalDateTime finalStartDate = startDate != null ? startDate : LocalDateTime.now().minusDays(7);
                    LocalDateTime finalEndDate = endDate != null ? endDate : LocalDateTime.now();
                    syncService.syncOrders(store, finalStartDate, finalEndDate);
                    return ResponseEntity.ok("주문 동기화가 시작되었습니다.");
                })
                .orElse(ResponseEntity.badRequest()
                        .body("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
    }
}

