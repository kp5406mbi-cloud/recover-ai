package com.recoverai.simulation;

import com.recoverai.service.RetryPolicyService;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class SimulationService {

    private final RetryPolicyService retryPolicyService;

    public SimulationService(RetryPolicyService retryPolicyService) {
        this.retryPolicyService = retryPolicyService;
    }

    public SimulationResult run(Integer requestedCount) {

        int count = requestedCount == null ? 25 : requestedCount;

        if (count < 1) {
            throw new IllegalArgumentException(
                    "Simulation count must be at least 1"
            );
        }

        if (count > 500) {
            throw new IllegalArgumentException(
                    "Simulation count cannot exceed 500"
            );
        }

        Random random = new Random(42L);

        String[] failureReasons = {
                "INSUFFICIENT_FUNDS",
                "NETWORK_ERROR",
                "TIMEOUT",
                "EXPIRED_CARD",
                "PAYMENT_METHOD_DECLINED"
        };

        double revenueAtRisk = 0.0;
        double recoveredAmount = 0.0;

        int recoveredPayments = 0;
        int manualReviewPayments = 0;
        int unrecoveredPayments = 0;

        int totalAttempts = 0;
        int successfulAttempts = 0;
        int failedAttempts = 0;

        for (int i = 0; i < count; i++) {

            double amount =
                    500.0 + random.nextInt(19500);

            amount = Math.round(amount / 100.0) * 100.0;

            String failureReason =
                    failureReasons[random.nextInt(
                            failureReasons.length
                    )];

            revenueAtRisk += amount;

            boolean retryable =
                    retryPolicyService.isRetryable(failureReason);

            int maxAttempts =
                    retryPolicyService.getMaxAttempts(
                            failureReason
                    );

            if (!retryable || maxAttempts <= 0) {
                manualReviewPayments++;
                continue;
            }

            boolean recovered = false;

            for (int attempt = 1;
                    attempt <= maxAttempts;
                    attempt++) {

                totalAttempts++;

                double recoveryProbability =
                        getRecoveryProbability(
                                failureReason,
                                attempt
                        );

                boolean success =
                        random.nextDouble()
                                < recoveryProbability;

                if (success) {
                    recovered = true;
                    successfulAttempts++;
                    recoveredPayments++;
                    recoveredAmount += amount;
                    break;
                }

                failedAttempts++;
            }

            if (!recovered) {
                unrecoveredPayments++;
                manualReviewPayments++;
            }
        }

        double recoveryRate =
                revenueAtRisk == 0.0
                        ? 0.0
                        : (recoveredAmount / revenueAtRisk) * 100.0;

        return new SimulationResult(
                count,
                round(revenueAtRisk),
                round(recoveredAmount),
                round(recoveryRate),
                recoveredPayments,
                manualReviewPayments,
                unrecoveredPayments,
                totalAttempts,
                successfulAttempts,
                failedAttempts
        );
    }

    private double getRecoveryProbability(
            String failureReason,
            int attemptNumber) {

        double baseProbability =
                switch (failureReason.toUpperCase()) {

                    case "INSUFFICIENT_FUNDS" -> 0.70;
                    case "NETWORK_ERROR" -> 0.55;
                    case "TIMEOUT" -> 0.60;

                    default -> 0.0;
                };

        /*
         * A later retry is slightly less likely to succeed.
         * This keeps the simulation realistic while preserving
         * bounded retry behavior.
         */
        double reduction =
                Math.max(0, attemptNumber - 1) * 0.10;

        return Math.max(
                0.0,
                baseProbability - reduction
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
