package com.recoverai.service;

import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.repository.PaymentRepository;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryDecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RecoveryExecutionService {

    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryDecisionRepository recoveryDecisionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderSimulator paymentProviderSimulator;
    private final RetryPolicyService retryPolicyService;

    public RecoveryExecutionService(
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryDecisionRepository recoveryDecisionRepository,
            PaymentRepository paymentRepository,
            PaymentProviderSimulator paymentProviderSimulator,
            RetryPolicyService retryPolicyService) {

        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryDecisionRepository = recoveryDecisionRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProviderSimulator = paymentProviderSimulator;
        this.retryPolicyService = retryPolicyService;
    }

    /*
     * =========================================================
     * SCHEDULED EXECUTION
     * =========================================================
     *
     * Called by RecoveryScheduler.
     */
    @Transactional
    public void execute(RecoveryAttempt attempt) {

        executeInternal(attempt, false);
    }

    /*
     * =========================================================
     * IMMEDIATE EXECUTION
     * =========================================================
     *
     * Called by the frontend when the user clicks
     * "Execute Recovery".
     */
    @Transactional
    public RecoveryAttempt executeNow(Long attemptId) {

        RecoveryAttempt attempt =
                recoveryAttemptRepository.findById(attemptId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Recovery attempt not found: " + attemptId
                                )
                        );

        executeInternal(attempt, true);

        return attempt;
    }

    /*
     * =========================================================
     * CORE EXECUTION LOGIC
     * =========================================================
     */
    private void executeInternal(
            RecoveryAttempt attempt,
            boolean immediateExecution) {

        /*
         * Idempotency protection.
         *
         * SUCCESS / FAILED / SKIPPED attempts cannot
         * be executed again.
         */
        if (!"SCHEDULED".equalsIgnoreCase(attempt.getStatus())) {
            return;
        }

        /*
         * Scheduled executions respect scheduledAt.
         *
         * Manual execution bypasses the scheduled time.
         */
        if (!immediateExecution
                && attempt.getScheduledAt() != null
                && attempt.getScheduledAt().isAfter(Instant.now())) {

            return;
        }

        Payment payment =
                paymentRepository.findById(
                        attempt.getPaymentId()
                ).orElse(null);

        /*
         * =====================================================
         * PAYMENT NOT FOUND
         * =====================================================
         */
        if (payment == null) {

            attempt.setStatus("FAILED");
            attempt.setExecutedAt(Instant.now());
            attempt.setResult("Payment not found");

            recoveryAttemptRepository.save(attempt);

            updateLatestDecision(
                    attempt.getPaymentId(),
                    "FAILED",
                    "Payment not found"
            );

            return;
        }

        /*
         * =====================================================
         * PAYMENT NO LONGER ELIGIBLE
         * =====================================================
         */
        if (!"FAILED".equalsIgnoreCase(payment.getStatus())) {

            attempt.setStatus("SKIPPED");
            attempt.setExecutedAt(Instant.now());
            attempt.setResult(
                    "Payment is no longer eligible for recovery"
            );

            recoveryAttemptRepository.save(attempt);

            updateLatestDecision(
                    payment.getId(),
                    "SKIPPED",
                    "Payment is no longer eligible for recovery"
            );

            return;
        }

        /*
         * =====================================================
         * EXECUTE PAYMENT RETRY
         * =====================================================
         */
        attempt.setExecutedAt(Instant.now());

        boolean successful =
                paymentProviderSimulator.retry(payment);

        /*
         * =====================================================
         * SUCCESS
         * =====================================================
         */
        if (successful) {

            payment.setStatus("SUCCESS");

            payment.setRetryCount(
                    payment.getRetryCount() + 1
            );

            attempt.setStatus("SUCCESS");

            attempt.setResult(
                    "Payment recovered successfully"
            );

            paymentRepository.save(payment);
            recoveryAttemptRepository.save(attempt);

            updateLatestDecision(
                    payment.getId(),
                    "SUCCESS",
                    "Payment recovered successfully"
            );

            return;
        }

        /*
         * =====================================================
         * CURRENT RETRY FAILED
         * =====================================================
         */
        payment.setRetryCount(
                payment.getRetryCount() + 1
        );

        attempt.setStatus("FAILED");

        attempt.setResult(
                "Payment retry failed"
        );

        paymentRepository.save(payment);
        recoveryAttemptRepository.save(attempt);

        updateLatestDecision(
                payment.getId(),
                "FAILED",
                "Payment retry failed"
        );

        /*
         * =====================================================
         * DETERMINE WHETHER ANOTHER RETRY IS ALLOWED
         * =====================================================
         */

        String failureReason =
                payment.getFailureReason();

        boolean retryable =
                retryPolicyService.isRetryable(
                        failureReason
                );

        int maxAttempts =
                retryPolicyService.getMaxAttempts(
                        failureReason
                );

        int nextAttempt =
                payment.getRetryCount() + 1;

        /*
         * Another automated retry is permitted.
         */
        if (retryable && nextAttempt <= maxAttempts) {

            RecoveryAttempt nextRecoveryAttempt =
                    new RecoveryAttempt();

            nextRecoveryAttempt.setPaymentId(
                    payment.getId()
            );

            nextRecoveryAttempt.setAttemptNumber(
                    nextAttempt
            );

            nextRecoveryAttempt.setStrategy(
                    attempt.getStrategy()
            );

            nextRecoveryAttempt.setStatus(
                    "SCHEDULED"
            );

            Instant scheduledAt =
                    Instant.now().plus(
                            retryPolicyService.getRetryDelay(
                                    failureReason,
                                    nextAttempt
                            )
                    );

            nextRecoveryAttempt.setScheduledAt(
                    scheduledAt
            );

            recoveryAttemptRepository.save(
                    nextRecoveryAttempt
            );

        } else {

            /*
             * Retry limit exhausted.
             *
             * Stop automation and require human review.
             */
            payment.setStatus("MANUAL_REVIEW");

            paymentRepository.save(payment);
        }
    }

    /*
     * =========================================================
     * UPDATE AI DECISION AUDIT TRAIL
     * =========================================================
     */
    private void updateLatestDecision(
            Long paymentId,
            String outcome,
            String result) {

        recoveryDecisionRepository
                .findTopByPaymentIdOrderByRecommendedAtDesc(
                        paymentId
                )
                .ifPresent(decision -> {

                    decision.setExecutedAt(
                            Instant.now()
                    );

                    decision.setOutcome(
                            outcome + ": " + result
                    );

                    recoveryDecisionRepository.save(
                            decision
                    );
                });
    }
}