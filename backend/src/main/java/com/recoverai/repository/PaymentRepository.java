package com.recoverai.repository;

import com.recoverai.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatus(String status);

    List<Payment> findByCustomerId(String customerId);
}