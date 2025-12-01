package com.example.auto.service;

import com.example.auto.domain.Store;
import com.example.auto.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StoreService {
    
    private final StoreRepository storeRepository;
    
    /**
     * 스토어 등록 (독립 실행형: 스토어는 1개만 등록 가능)
     */
    public Store createStore(Store store) {
        // 이미 스토어가 등록되어 있으면 에러
        if (storeRepository.count() > 0) {
            throw new IllegalArgumentException("이미 스토어가 등록되어 있습니다. 스토어는 1개만 등록할 수 있습니다.");
        }
        if (storeRepository.existsByVendorId(store.getVendorId())) {
            throw new IllegalArgumentException("이미 등록된 스토어 ID입니다: " + store.getVendorId());
        }
        store.setActive(true); // 자동으로 활성화
        return storeRepository.save(store);
    }
    
    public Store updateStore(Long id, Store store) {
        Store existingStore = storeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + id));
        
        existingStore.setStoreName(store.getStoreName());
        existingStore.setAccessToken(store.getAccessToken());
        existingStore.setRefreshToken(store.getRefreshToken());
        existingStore.setTokenExpiresAt(store.getTokenExpiresAt());
        existingStore.setActive(store.isActive());
        
        return storeRepository.save(existingStore);
    }
    
    /**
     * 현재 등록된 스토어 조회 (독립 실행형: 스토어는 1개만 존재)
     */
    @Transactional(readOnly = true)
    public Optional<Store> getCurrentStore() {
        return storeRepository.findByIsActiveTrue().stream().findFirst();
    }
    
    @Transactional(readOnly = true)
    public Optional<Store> getStoreById(Long id) {
        return storeRepository.findById(id);
    }
    
    // 하위 호환성을 위해 유지 (사용하지 않음)
    @Transactional(readOnly = true)
    @Deprecated
    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    @Deprecated
    public List<Store> getActiveStores() {
        return storeRepository.findByIsActiveTrue();
    }
    
    @Transactional(readOnly = true)
    public Optional<Store> getStoreByVendorId(String vendorId) {
        return storeRepository.findByVendorId(vendorId);
    }
    
    public void deleteStore(Long id) {
        storeRepository.deleteById(id);
    }
}

