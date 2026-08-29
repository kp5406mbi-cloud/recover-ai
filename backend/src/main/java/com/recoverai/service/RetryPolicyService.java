package com.recoverai.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RetryPolicyService {

    public boolean isRetryable(String failureReason) {

        if (failureReason == null) {
            return false;
        }

        return switch (failureReason.toUpperCase()) {
            case "INSUFFICIENT_FUNDS",
                 "NETWORK_ERROR",
                 "TIMEOUT" -> true;

            default -> false;
        };
    }

    public int getMaxAttempts(String failureReason) {

        if (failureReason == null) {
            return 0;
        }

        return switch (failureReason.toUpperCase()) {
            case "INSUFFICIENT_FUNDS" -> 3;
            case "NETWORK_ERROR" -> 4;
            case "TIMEOUT" -> 3;

            default -> 0;
        };
    }

    public Duration getRetryDelay(
            String failureReason,
            int attemptNumber) {

        if (attemptNumber <= 0) {
            return Duration.ZERO;
        }

        return switch (failureReason.toUpperCase()) {

            case "NETWORK_ERROR",
                 "TIMEOUT" ->
                    exponentialBackoff(
                            Duration.ofSeconds(30),
                            attemptNumber
                    );

            case "INSUFFICIENT_FUNDS" ->
                    exponentialBackoff(
                            Duration.ofMinutes(1),
                            attemptNumber
                    );

            default ->
                    Duration.ZERO;
        };
    }

    private Duration exponentialBackoff(
            Duration initialDelay,
            int attemptNumber) {

        long multiplier = 1L << (attemptNumber - 1);

        return initialDelay.multipliedBy(multiplier);
    }
}