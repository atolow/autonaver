package com.example.auto.repository;

import com.example.auto.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    
    Optional<Store> findByVendorId(String vendorId);
    
    List<Store> findByIsActiveTrue();
    
    boolean existsByVendorId(String vendorId);
}

