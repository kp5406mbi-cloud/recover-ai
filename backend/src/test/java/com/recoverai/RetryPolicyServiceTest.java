package com.recoverai;

import com.recoverai.service.RetryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyServiceTest {

    private RetryPolicyService retryPolicyService;

    @BeforeEach
    void setUp() {
        retryPolicyService = new RetryPolicyService();
    }

    @Test
    void insufficientFundsShouldBeRetryable() {
        assertTrue(
                retryPolicyService.isRetryable("INSUFFICIENT_FUNDS")
        );
    }

    @Test
    void networkErrorShouldBeRetryable() {
        assertTrue(
                retryPolicyService.isRetryable("NETWORK_ERROR")
        );
    }

    @Test
    void timeoutShouldBeRetryable() {
        assertTrue(
                retryPolicyService.isRetryable("TIMEOUT")
        );
    }

    @Test
    void cardExpiredShouldNotBeRetryable() {
        assertFalse(
                retryPolicyService.isRetryable("CARD_EXPIRED")
        );
    }

    @Test
    void nullFailureReasonShouldNotBeRetryable() {
        assertFalse(
                retryPolicyService.isRetryable(null)
        );
    }

    @Test
    void insufficientFundsShouldAllowThreeAttempts() {
        assertEquals(
                3,
                retryPolicyService.getMaxAttempts("INSUFFICIENT_FUNDS")
        );
    }

    @Test
    void networkErrorShouldAllowFourAttempts() {
        assertEquals(
                4,
                retryPolicyService.getMaxAttempts("NETWORK_ERROR")
        );
    }

    @Test
    void timeoutShouldAllowThreeAttempts() {
        assertEquals(
                3,
                retryPolicyService.getMaxAttempts("TIMEOUT")
        );
    }

    @Test
    void nonRetryableReasonShouldAllowZeroAttempts() {
        assertEquals(
                0,
                retryPolicyService.getMaxAttempts("CARD_EXPIRED")
        );
    }

    @Test
    void insufficientFundsFirstAttemptShouldHaveOneMinuteDelay() {
        assertEquals(
                Duration.ofMinutes(1),
                retryPolicyService.getRetryDelay(
                        "INSUFFICIENT_FUNDS",
                        1
                )
        );
    }

    @Test
    void insufficientFundsSecondAttemptShouldHaveTwoMinuteDelay() {
        assertEquals(
                Duration.ofMinutes(2),
                retryPolicyService.getRetryDelay(
                        "INSUFFICIENT_FUNDS",
                        2
                )
        );
    }

    @Test
    void insufficientFundsThirdAttemptShouldHaveFourMinuteDelay() {
        assertEquals(
                Duration.ofMinutes(4),
                retryPolicyService.getRetryDelay(
                        "INSUFFICIENT_FUNDS",
                        3
                )
        );
    }

    @Test
    void networkErrorShouldUseExponentialBackoff() {
        assertEquals(
                Duration.ofSeconds(30),
                retryPolicyService.getRetryDelay(
                        "NETWORK_ERROR",
                        1
                )
        );

        assertEquals(
                Duration.ofSeconds(60),
                retryPolicyService.getRetryDelay(
                        "NETWORK_ERROR",
                        2
                )
        );

        assertEquals(
                Duration.ofSeconds(120),
                retryPolicyService.getRetryDelay(
                        "NETWORK_ERROR",
                        3
                )
        );
    }

    @Test
    void invalidAttemptNumberShouldHaveZeroDelay() {
        assertEquals(
                Duration.ZERO,
                retryPolicyService.getRetryDelay(
                        "TIMEOUT",
                        0
                )
        );
    }
}