package com.example.auto.repository;

import com.example.auto.domain.Product;
import com.example.auto.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findByStoreAndProductId(Store store, String productId);
    
    List<Product> findByStore(Store store);
    
    List<Product> findByStoreAndStatus(Store store, Product.ProductStatus status);
}

