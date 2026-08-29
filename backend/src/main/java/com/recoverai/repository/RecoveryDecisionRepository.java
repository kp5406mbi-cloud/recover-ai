package com.recoverai.repository;

import com.recoverai.entity.RecoveryDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryDecisionRepository
        extends JpaRepository<RecoveryDecision, Long> {

    List<RecoveryDecision> findByPaymentId(Long paymentId);

    Optional<RecoveryDecision>
    findTopByPaymentIdOrderByRecommendedAtDesc(
            Long paymentId
    );
}