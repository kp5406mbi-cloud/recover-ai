package com.recoverai.simulation;

public record SimulationResult(
        int paymentsAnalyzed,
        double revenueAtRisk,
        double recoveredAmount,
        double recoveryRate,
        int recoveredPayments,
        int manualReviewPayments,
        int unrecoveredPayments,
        int totalAttempts,
        int successfulAttempts,
        int failedAttempts
) {
}
