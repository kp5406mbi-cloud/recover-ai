package com.recoverai.repository;

import com.recoverai.entity.RecoveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RecoveryAttemptRepository
        extends JpaRepository<RecoveryAttempt, Long> {

    List<RecoveryAttempt> findByPaymentId(Long paymentId);

    List<RecoveryAttempt> findByStatus(String status);

    List<RecoveryAttempt> findByStatusAndScheduledAtLessThanEqual(
            String status,
            Instant scheduledAt
    );
}