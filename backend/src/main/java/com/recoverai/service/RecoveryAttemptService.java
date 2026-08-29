package com.recoverai.service;

import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RecoveryAttemptService {

    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public RecoveryAttemptService(
            RecoveryAttemptRepository recoveryAttemptRepository) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }

    public RecoveryAttempt createAttempt(RecoveryAttempt attempt) {
        if (attempt.getStatus() == null || attempt.getStatus().isBlank()) {
            attempt.setStatus("SCHEDULED");
        }

        return recoveryAttemptRepository.save(attempt);
    }

    public List<RecoveryAttempt> getAllAttempts() {
        return recoveryAttemptRepository.findAll();
    }

    public Optional<RecoveryAttempt> getAttemptById(Long id) {
        return recoveryAttemptRepository.findById(id);
    }

    public List<RecoveryAttempt> getAttemptsByPayment(Long paymentId) {
        return recoveryAttemptRepository.findByPaymentId(paymentId);
    }

    public List<RecoveryAttempt> getAttemptsByStatus(String status) {
        return recoveryAttemptRepository.findByStatus(status);
    }

    public void deleteAttempt(Long id) {
        recoveryAttemptRepository.deleteById(id);
    }
}