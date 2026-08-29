package com.recoverai.service;

import com.recoverai.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentProviderSimulator {

    @Value("${recover-ai.simulator.force-failure:false}")
    private boolean forceFailure;

    public boolean retry(Payment payment) {

        /*
         * Simulated payment provider.
         *
         * forceFailure=true:
         *     every retry fails.
         *
         * forceFailure=false:
         *     INSUFFICIENT_FUNDS retries succeed.
         */

        if (forceFailure) {
            return false;
        }

        if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(
                payment.getFailureReason())) {

            return true;
        }

        return false;
    }
}