package com.recoverai.service;

import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.repository.RecoveryAttemptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RecoveryScheduler {

    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryExecutionService recoveryExecutionService;

    public RecoveryScheduler(
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryExecutionService recoveryExecutionService) {

        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryExecutionService = recoveryExecutionService;
    }

    @Scheduled(fixedDelay = 10000)
    public void executeDueRecoveries() {

        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository
                        .findByStatusAndScheduledAtLessThanEqual(
                                "SCHEDULED",
                                Instant.now()
                        );

        for (RecoveryAttempt attempt : attempts) {
            recoveryExecutionService.execute(attempt);
        }
    }
}