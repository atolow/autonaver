package com.example.auto.controller;

import com.example.auto.domain.Store;
import com.example.auto.service.StoreService;
import com.example.auto.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 동기화 REST API
 */
@Slf4j
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
    public ResponseEntity<Map<String, Object>> syncProducts() {
        return storeService.getCurrentStore()
                .map(store -> {
                    try {
                        int syncedCount = syncService.syncProducts(store);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "상품 동기화가 완료되었습니다.");
                        response.put("syncedCount", syncedCount);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        log.error("상품 동기화 실패", e);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "상품 동기화 실패: " + e.getMessage());
                        response.put("error", e.getClass().getSimpleName());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                });
    }
    
    /**
     * 주문 동기화 (독립 실행형: 현재 스토어만 동기화)
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> syncOrders(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        
        return storeService.getCurrentStore()
                .map(store -> {
                    try {
                        LocalDateTime finalStartDate = startDate != null ? startDate : LocalDateTime.now().minusDays(7);
                        LocalDateTime finalEndDate = endDate != null ? endDate : LocalDateTime.now();
                        syncService.syncOrders(store, finalStartDate, finalEndDate);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "주문 동기화가 시작되었습니다.");
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        log.error("주문 동기화 실패", e);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "주문 동기화 실패: " + e.getMessage());
                        response.put("error", e.getClass().getSimpleName());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                });
    }
}

