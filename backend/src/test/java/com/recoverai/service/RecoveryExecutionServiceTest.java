package com.recoverai.service;

import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryDecision;
import com.recoverai.repository.PaymentRepository;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryExecutionServiceTest {

    @Mock
    private RecoveryAttemptRepository recoveryAttemptRepository;

    @Mock
    private RecoveryDecisionRepository recoveryDecisionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProviderSimulator paymentProviderSimulator;

    @Mock
    private RetryPolicyService retryPolicyService;

    private RecoveryExecutionService service;

    @BeforeEach
    void setUp() {

        service = new RecoveryExecutionService(
                recoveryAttemptRepository,
                recoveryDecisionRepository,
                paymentRepository,
                paymentProviderSimulator,
                retryPolicyService
        );
    }

    private Payment createFailedPayment() {

        Payment payment = new Payment();

        payment.setId(1L);
        payment.setCustomerId("cust_test");
        payment.setAmount(
                new BigDecimal("1000.00")
        );
        payment.setCurrency("INR");
        payment.setStatus("FAILED");
        payment.setFailureReason(
                "INSUFFICIENT_FUNDS"
        );
        payment.setRetryCount(0);

        return payment;
    }

    private RecoveryAttempt createScheduledAttempt() {

        RecoveryAttempt attempt =
                new RecoveryAttempt();



        attempt.setPaymentId(1L);

        attempt.setAttemptNumber(1);

        attempt.setStrategy(
                "RETRY_LATER"
        );

        attempt.setStatus(
                "SCHEDULED"
        );

        attempt.setScheduledAt(
                Instant.now().minusSeconds(1)
        );

        return attempt;
    }

    private RecoveryDecision createDecision() {

        RecoveryDecision decision =
                new RecoveryDecision();

        decision.setPaymentId(1L);
        decision.setStrategy("RETRY_LATER");
        decision.setReason(
                "Temporary payment failure"
        );
        decision.setConfidence(0.90);

        return decision;
    }


    /*
     * =========================================================
     * SUCCESSFUL RETRY
     * =========================================================
     */

    @Test
    void successfulRetryShouldMarkPaymentSuccess() {

        Payment payment =
                createFailedPayment();

        RecoveryAttempt attempt =
                createScheduledAttempt();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(paymentProviderSimulator.retry(payment))
                .thenReturn(true);

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        assertEquals(
                "SUCCESS",
                payment.getStatus()
        );

        assertEquals(
                1,
                payment.getRetryCount()
        );

        assertEquals(
                "SUCCESS",
                attempt.getStatus()
        );

        assertEquals(
                "Payment recovered successfully",
                attempt.getResult()
        );

        assertNotNull(
                attempt.getExecutedAt()
        );

        verify(paymentRepository)
                .save(payment);

        verify(recoveryAttemptRepository)
                .save(attempt);
    }


    /*
     * =========================================================
     * FAILED RETRY
     * =========================================================
     */

    @Test
    void failedRetryShouldIncrementRetryCount() {

        Payment payment =
                createFailedPayment();

        RecoveryAttempt attempt =
                createScheduledAttempt();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(paymentProviderSimulator.retry(payment))
                .thenReturn(false);

        when(
                retryPolicyService
                        .isRetryable(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(true);

        when(
                retryPolicyService
                        .getMaxAttempts(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(3);

        when(
                retryPolicyService.getRetryDelay(
                        eq("INSUFFICIENT_FUNDS"),
                        eq(2)
                )
        ).thenReturn(
                java.time.Duration.ofMinutes(1)
        );

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        assertEquals(
                "FAILED",
                payment.getStatus()
        );

        assertEquals(
                1,
                payment.getRetryCount()
        );

        assertEquals(
                "FAILED",
                attempt.getStatus()
        );

        assertEquals(
                "Payment retry failed",
                attempt.getResult()
        );

        verify(paymentRepository)
                .save(payment);

        verify(
                recoveryAttemptRepository,
                times(2)
        ).save(any(RecoveryAttempt.class));
    }


    /*
     * =========================================================
     * NEXT RETRY
     * =========================================================
     */

    @Test
    void failedRetryShouldScheduleNextAttemptWhenAllowed() {

        Payment payment =
                createFailedPayment();

        RecoveryAttempt attempt =
                createScheduledAttempt();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(paymentProviderSimulator.retry(payment))
                .thenReturn(false);

        when(
                retryPolicyService
                        .isRetryable(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(true);

        when(
                retryPolicyService
                        .getMaxAttempts(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(3);

        when(
                retryPolicyService.getRetryDelay(
                        eq("INSUFFICIENT_FUNDS"),
                        eq(2)
                )
        ).thenReturn(
                java.time.Duration.ofMinutes(1)
        );

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        ArgumentCaptor<RecoveryAttempt>
                captor =
                ArgumentCaptor.forClass(
                        RecoveryAttempt.class
                );

        verify(
                recoveryAttemptRepository,
                times(2)
        ).save(captor.capture());

        RecoveryAttempt nextAttempt =
                captor.getAllValues().get(1);

        assertEquals(
                2,
                nextAttempt.getAttemptNumber()
        );

        assertEquals(
                1L,
                nextAttempt.getPaymentId()
        );

        assertEquals(
                "RETRY_LATER",
                nextAttempt.getStrategy()
        );

        assertEquals(
                "SCHEDULED",
                nextAttempt.getStatus()
        );

        assertNotNull(
                nextAttempt.getScheduledAt()
        );
    }


    /*
     * =========================================================
     * RETRY EXHAUSTION
     * =========================================================
     */

    @Test
    void retryExhaustionShouldMovePaymentToManualReview() {

        Payment payment =
                createFailedPayment();

        /*
         * This execution is attempt #3.
         */
        payment.setRetryCount(2);

        RecoveryAttempt attempt =
                createScheduledAttempt();

        attempt.setAttemptNumber(3);

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(paymentProviderSimulator.retry(payment))
                .thenReturn(false);

        when(
                retryPolicyService
                        .isRetryable(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(true);

        when(
                retryPolicyService
                        .getMaxAttempts(
                                "INSUFFICIENT_FUNDS"
                        )
        ).thenReturn(3);

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        assertEquals(
                3,
                payment.getRetryCount()
        );

        assertEquals(
                "MANUAL_REVIEW",
                payment.getStatus()
        );

        assertEquals(
                "FAILED",
                attempt.getStatus()
        );

        verify(
                paymentRepository,
                times(2)
        ).save(payment);

        verify(
                recoveryAttemptRepository
        ).save(attempt);
    }


    /*
     * =========================================================
     * NON-FAILED PAYMENT
     * =========================================================
     */

    @Test
    void nonFailedPaymentShouldBeSkipped() {

        Payment payment =
                createFailedPayment();

        payment.setStatus("SUCCESS");

        RecoveryAttempt attempt =
                createScheduledAttempt();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        assertEquals(
                "SKIPPED",
                attempt.getStatus()
        );

        assertEquals(
                "Payment is no longer eligible for recovery",
                attempt.getResult()
        );

        assertNotNull(
                attempt.getExecutedAt()
        );

        verify(
                paymentProviderSimulator,
                never()
        ).retry(any(Payment.class));

        verify(
                recoveryAttemptRepository
        ).save(attempt);

        verify(
                paymentRepository,
                never()
        ).save(any(Payment.class));
    }


    /*
     * =========================================================
     * MISSING PAYMENT
     * =========================================================
     */

    @Test
    void missingPaymentShouldMarkAttemptFailed() {

        RecoveryAttempt attempt =
                createScheduledAttempt();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.empty());

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        service.execute(attempt);

        assertEquals(
                "FAILED",
                attempt.getStatus()
        );

        assertEquals(
                "Payment not found",
                attempt.getResult()
        );

        assertNotNull(
                attempt.getExecutedAt()
        );

        verify(
                recoveryAttemptRepository
        ).save(attempt);

        verify(
                paymentProviderSimulator,
                never()
        ).retry(any(Payment.class));
    }


    /*
     * =========================================================
     * ALREADY EXECUTED
     * =========================================================
     */

    @Test
    void alreadyExecutedAttemptShouldBeIgnored() {

        RecoveryAttempt attempt =
                createScheduledAttempt();

        attempt.setStatus("SUCCESS");

        service.execute(attempt);

        verify(
                paymentRepository,
                never()
        ).findById(anyLong());

        verify(
                paymentProviderSimulator,
                never()
        ).retry(any(Payment.class));

        verify(
                paymentRepository,
                never()
        ).save(any(Payment.class));

        verify(
                recoveryAttemptRepository,
                never()
        ).save(any(RecoveryAttempt.class));
    }


    /*
     * =========================================================
     * FUTURE SCHEDULE
     * =========================================================
     */

    @Test
    void futureScheduledAttemptShouldNotExecute() {

        RecoveryAttempt attempt =
                createScheduledAttempt();

        attempt.setScheduledAt(
                Instant.now().plusSeconds(60)
        );

        service.execute(attempt);

        verify(
                paymentRepository,
                never()
        ).findById(anyLong());

        verify(
                paymentProviderSimulator,
                never()
        ).retry(any(Payment.class));

        verify(
                recoveryAttemptRepository,
                never()
        ).save(any(RecoveryAttempt.class));
    }


    /*
     * =========================================================
     * DECISION AUDIT - SUCCESS
     * =========================================================
     */

    @Test
    void successfulExecutionShouldUpdateLatestDecision() {

        Payment payment =
                createFailedPayment();

        RecoveryAttempt attempt =
                createScheduledAttempt();

        RecoveryDecision decision =
                createDecision();

        when(paymentRepository.findById(1L))
                .thenReturn(Optional.of(payment));

        when(paymentProviderSimulator.retry(payment))
                .thenReturn(true);

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.of(decision));

        service.execute(attempt);

        assertEquals(
                "SUCCESS",
                decision.getOutcome()
                        .split(":")[0]
        );

        assertTrue(
                decision.getOutcome()
                        .contains(
                                "Payment recovered successfully"
                        )
        );

        assertNotNull(
                decision.getExecutedAt()
        );

        verify(
                recoveryDecisionRepository
        ).save(decision);
    }


    /*
     * =========================================================
     * IMMEDIATE EXECUTION
     * =========================================================
     */

    @Test
    void executeNowShouldExecuteScheduledAttemptImmediately() {

        Payment payment =
                createFailedPayment();

        RecoveryAttempt attempt =
                createScheduledAttempt();

        /*
         * Put the attempt in the future.
         *
         * executeNow() should bypass scheduledAt.
         */
        attempt.setScheduledAt(
                Instant.now().plusSeconds(3600)
        );

        when(
                recoveryAttemptRepository.findById(10L)
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findById(1L)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentProviderSimulator.retry(payment)
        ).thenReturn(true);

        when(
                recoveryDecisionRepository
                        .findTopByPaymentIdOrderByRecommendedAtDesc(
                                1L
                        )
        ).thenReturn(Optional.empty());

        RecoveryAttempt result =
                service.executeNow(10L);

        assertSame(
                attempt,
                result
        );

        assertEquals(
                "SUCCESS",
                attempt.getStatus()
        );

        assertEquals(
                "SUCCESS",
                payment.getStatus()
        );

        verify(
                paymentProviderSimulator
        ).retry(payment);
    }


    /*
     * =========================================================
     * UNKNOWN ATTEMPT
     * =========================================================
     */

    @Test
    void executeNowShouldThrowWhenAttemptDoesNotExist() {

        when(
                recoveryAttemptRepository.findById(999L)
        ).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.executeNow(999L)
        );

        verify(
                paymentRepository,
                never()
        ).findById(anyLong());

        verify(
                paymentProviderSimulator,
                never()
        ).retry(any(Payment.class));
    }
}