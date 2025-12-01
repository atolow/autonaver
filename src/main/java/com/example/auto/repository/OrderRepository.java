package com.example.auto.repository;

import com.example.auto.domain.Order;
import com.example.auto.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByStoreAndOrderId(Store store, String orderId);
    
    List<Order> findByStore(Store store);
    
    List<Order> findByStoreAndStatus(Store store, Order.OrderStatus status);
    
    List<Order> findByStoreAndOrderDateBetween(Store store, LocalDateTime startDate, LocalDateTime endDate);
}

