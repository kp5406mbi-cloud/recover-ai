package com.recoverai;

import com.recoverai.ai.AIRecoveryRecommendation;
import com.recoverai.ai.AIRecoveryService;
import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryDecision;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryDecisionRepository;
import com.recoverai.service.RetryPolicyService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RecoveryDecisionService {

    private final RecoveryDecisionRepository decisionRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RetryPolicyService retryPolicyService;
    private final AIRecoveryService aiRecoveryService;

    public RecoveryDecisionService(
            RecoveryDecisionRepository decisionRepository,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RetryPolicyService retryPolicyService,
            AIRecoveryService aiRecoveryService) {

        this.decisionRepository = decisionRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.retryPolicyService = retryPolicyService;
        this.aiRecoveryService = aiRecoveryService;
    }

    public RecoveryDecision classify(Payment payment) {

        if (!"FAILED".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException(
                    "Only failed payments can be classified for recovery"
            );
        }

        RecoveryDecision decision = new RecoveryDecision();

        decision.setPaymentId(payment.getId());

        String reason = payment.getFailureReason();

        /*
         * =========================================================
         * 1. DETERMINE DETERMINISTIC POLICY FACTS
         * =========================================================
         *
         * The policy engine determines what automation is permitted.
         * AI does not get to override these constraints.
         */

        boolean expiredCard =
                reason != null
                        && reason.equalsIgnoreCase("EXPIRED_CARD");

        boolean retryable =
                reason != null
                        && !reason.isBlank()
                        && retryPolicyService.isRetryable(reason);

        int previousAttempts =
                payment.getRetryCount();

        int maxAttempts = 0;

        if (retryable) {
            maxAttempts =
                    retryPolicyService.getMaxAttempts(reason);
        }

        /*
         * =========================================================
         * 2. AI DIAGNOSIS / RECOMMENDATION
         * =========================================================
         */

        AIRecoveryRecommendation aiRecommendation =
                aiRecoveryService.analyze(
                        payment,
                        previousAttempts,
                        maxAttempts
                );

        decision.setStrategy(
                aiRecommendation.getStrategy()
        );

        decision.setDiagnosis(
                aiRecommendation.getDiagnosis()
        );

        decision.setRecommendedAction(
                aiRecommendation.getRecommendedAction()
        );

        decision.setRiskLevel(
                aiRecommendation.getRiskLevel()
        );

        decision.setReason(
                aiRecommendation.getReasoning()
        );

        decision.setConfidence(
                aiRecommendation.getConfidence()
        );

        /*
         * =========================================================
         * 3. POLICY / SAFETY LAYER
         * =========================================================
         *
         * AI recommends.
         * Deterministic policy decides whether execution
         * is permitted.
         */

        decision.setMaxAttempts(maxAttempts);

        int nextAttempt =
                payment.getRetryCount() + 1;

        /*
         * ---------------------------------------------------------
         * EXPIRED CARD
         * ---------------------------------------------------------
         *
         * An expired card must never be automatically retried.
         *
         * However, this is not necessarily a generic manual-review
         * case. The appropriate recovery strategy is to ask the
         * customer to update the payment method.
         */

        if (expiredCard) {

            decision.setPolicyStatus("ESCALATE");

            decision.setStrategy(
                    "UPDATE_PAYMENT_METHOD"
            );

            decision.setRiskLevel(
                    "MEDIUM"
            );

            decision.setRecommendedAction(
                    "Ask the customer to update their payment method, then retry the payment."
            );

            decision.setReason(
                    safeReason(decision.getReason())
                            + " Automated retries are blocked because the payment method has expired."
            );

            /*
             * No RecoveryAttempt is scheduled here.
             *
             * The payment method must be updated before another
             * payment retry is appropriate.
             */

        }

        /*
         * ---------------------------------------------------------
         * NORMAL RETRYABLE FAILURE
         * ---------------------------------------------------------
         */

        else if (retryable && nextAttempt <= maxAttempts) {

            Duration retryDelay =
                    retryPolicyService.getRetryDelay(
                            reason,
                            nextAttempt
                    );

            decision.setRetryDelaySeconds(
                    retryDelay.getSeconds()
            );

            decision.setPolicyStatus(
                    "ALLOWED"
            );

        }

        /*
         * ---------------------------------------------------------
         * RETRY LIMIT EXHAUSTED
         * ---------------------------------------------------------
         */

        else if (retryable) {

            decision.setPolicyStatus(
                    "BLOCKED_RETRY_LIMIT"
            );

            decision.setRecommendedAction(
                    "Stop automated retries and escalate to manual review."
            );

            decision.setStrategy(
                    "MANUAL_REVIEW"
            );

            decision.setRiskLevel(
                    "HIGH"
            );

            decision.setReason(
                    safeReason(decision.getReason())
                            + " The maximum automated retry limit has been reached."
            );
        }

        /*
         * ---------------------------------------------------------
         * NON-RETRYABLE / UNKNOWN FAILURE
         * ---------------------------------------------------------
         */

        else {

            decision.setPolicyStatus(
                    "ESCALATE"
            );

            decision.setRecommendedAction(
                    "Do not retry automatically; escalate for appropriate recovery action."
            );

            decision.setStrategy(
                    "MANUAL_REVIEW"
            );

            decision.setRiskLevel(
                    "HIGH"
            );
        }

        /*
         * =========================================================
         * 4. SAVE DECISION
         * =========================================================
         */

        RecoveryDecision savedDecision =
                decisionRepository.save(decision);

        /*
         * =========================================================
         * 5. SCHEDULE BOUNDED AUTOMATED RECOVERY
         * =========================================================
         *
         * Only decisions explicitly permitted by the deterministic
         * policy layer can create an automated recovery attempt.
         */

        if ("ALLOWED".equals(savedDecision.getPolicyStatus())) {

            RecoveryAttempt attempt =
                    new RecoveryAttempt();

            attempt.setPaymentId(
                    payment.getId()
            );

            attempt.setAttemptNumber(
                    nextAttempt
            );

            attempt.setStrategy(
                    savedDecision.getStrategy()
            );

            attempt.setStatus(
                    "SCHEDULED"
            );

            Duration retryDelay =
                    retryPolicyService.getRetryDelay(
                            reason,
                            nextAttempt
                    );

            Instant scheduledAt =
                    Instant.now().plus(retryDelay);

            attempt.setScheduledAt(
                    scheduledAt
            );

            recoveryAttemptRepository.save(
                    attempt
            );
        }

        /*
         * EXPIRED_CARD, MANUAL_REVIEW and other ESCALATE decisions
         * deliberately do not create automated recovery attempts.
         */

        return savedDecision;
    }

    private String safeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return "Recovery policy applied.";
        }

        return reason;
    }
}