package com.recoverai.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.recoverai.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Service
public class AIRecoveryService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    public AIRecoveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    public AIRecoveryRecommendation analyze(
            Payment payment,
            int previousAttempts,
            int maxAttempts
    ) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured"
            );
        }

        String prompt = """
                You are the AI decision engine for RecoverAI,
                a payment revenue recovery system.

                Your job is to analyze a failed payment and recommend
                a recovery strategy.

                IMPORTANT:
                You are ONLY making a recommendation.

                A separate deterministic policy engine will decide
                whether the recommendation is actually allowed.

                Never authorize a payment.
                Never override retry limits.
                Never invent information.

                Available strategies:
                - RETRY_SOON
                - RETRY_LATER
                - UPDATE_PAYMENT_METHOD
                - MANUAL_REVIEW

                Payment:
                ID: %d
                Customer: %s
                Amount: %.2f %s
                Failure reason: %s
                Previous retry attempts: %d
                Maximum allowed attempts: %d

                Analyze:
                1. likely cause of failure
                2. whether recovery is likely
                3. appropriate recovery strategy
                4. customer/payment risk
                5. confidence from 0.0 to 1.0
                6. recommended action
                7. concise reasoning

                The strategy must be one of the allowed strategies.
                The risk level must be LOW, MEDIUM, or HIGH.
                """
                .formatted(
                        payment.getId(),
                        payment.getCustomerId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getFailureReason(),
                        previousAttempts,
                        maxAttempts
                );

        String schema = """
                {
                  "type": "OBJECT",
                  "properties": {
                    "diagnosis": {
                      "type": "STRING",
                      "description": "Likely cause and diagnosis of the payment failure."
                    },
                    "strategy": {
                      "type": "STRING",
                      "enum": [
                        "RETRY_SOON",
                        "RETRY_LATER",
                        "UPDATE_PAYMENT_METHOD",
                        "MANUAL_REVIEW"
                      ],
                      "description": "Recommended recovery strategy."
                    },
                    "confidence": {
                      "type": "NUMBER",
                      "description": "Confidence in the recommendation between 0.0 and 1.0."
                    },
                    "riskLevel": {
                      "type": "STRING",
                      "enum": [
                        "LOW",
                        "MEDIUM",
                        "HIGH"
                      ],
                      "description": "Risk level associated with the recovery recommendation."
                    },
                    "recommendedAction": {
                      "type": "STRING",
                      "description": "Concrete recommended recovery action."
                    },
                    "reasoning": {
                      "type": "STRING",
                      "description": "Concise reasoning supporting the recommendation."
                    }
                  },
                  "required": [
                    "diagnosis",
                    "strategy",
                    "confidence",
                    "riskLevel",
                    "recommendedAction",
                    "reasoning"
                  ]
                }
                """;

        String requestBody = """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        {
                          "text": %s
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "responseMimeType": "application/json",
                    "responseSchema": %s
                  }
                }
                """
                .formatted(
                        objectMapper.valueToTree(prompt).toString(),
                        schema
                );

        try {

            // Gemini request with retry handling for temporary 503 errors
            String response = callGeminiWithRetry(requestBody);

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode candidates =
                    root.path("candidates");

            if (!candidates.isArray()
                    || candidates.isEmpty()) {

                throw new IllegalStateException(
                        "Gemini response did not contain candidates"
                );
            }

            String outputText = null;

            for (JsonNode candidate : candidates) {

                JsonNode content =
                        candidate.path("content");

                JsonNode parts =
                        content.path("parts");

                if (!parts.isArray()) {
                    continue;
                }

                for (JsonNode part : parts) {

                    JsonNode text =
                            part.path("text");

                    if (!text.isMissingNode()
                            && !text.isNull()) {

                        String value =
                                text.asText();

                        if (value != null
                                && !value.isBlank()) {

                            outputText = value;
                            break;
                        }
                    }
                }

                if (outputText != null
                        && !outputText.isBlank()) {
                    break;
                }
            }

            if (outputText == null
                    || outputText.isBlank()) {

                throw new IllegalStateException(
                        "Gemini response did not contain recommendation text"
                );
            }

            return objectMapper.readValue(
                    outputText,
                    AIRecoveryRecommendation.class
            );

        } catch (Exception e) {

            return createFallbackRecommendation(
                    payment,
                    previousAttempts,
                    maxAttempts
            );
        }
    }

    /**
     * Calls Gemini and retries temporary 503 Service Unavailable
     * responses a limited number of times.
     */
    private String callGeminiWithRetry(String requestBody) {

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                return restClient.post()
                        .uri(
                                "/models/{model}:generateContent",
                                model
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", apiKey)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

            } catch (HttpServerErrorException.ServiceUnavailable e) {

                // If this was the final attempt, propagate the error.
                if (attempt == maxAttempts) {
                    throw e;
                }

                try {

                    // Wait 1 second before attempt 2,
                    // 2 seconds before attempt 3.
                    Thread.sleep(1000L * attempt);

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Gemini retry interrupted",
                            interruptedException
                    );
                }
            }
        }

        throw new IllegalStateException(
                "Gemini request failed after retries"
        );
    }

    /**
     * Deterministic fallback used when Gemini is unavailable,
     * rate-limited, or returns an invalid response.
     *
     * The fallback only provides a recommendation.
     * RecoveryDecisionService remains responsible for enforcing
     * the actual recovery policy.
     */
    private AIRecoveryRecommendation createFallbackRecommendation(
            Payment payment,
            int previousAttempts,
            int maxAttempts
    ) {

        AIRecoveryRecommendation recommendation =
                new AIRecoveryRecommendation();

        String failureReason = payment.getFailureReason();

        /*
         * Expired card:
         * Do not retry automatically.
         */
        if ("EXPIRED_CARD".equalsIgnoreCase(failureReason)) {

            recommendation.setDiagnosis(
                    "The payment method has expired."
            );

            recommendation.setStrategy(
                    "UPDATE_PAYMENT_METHOD"
            );

            recommendation.setConfidence(1.0);

            recommendation.setRiskLevel(
                    "MEDIUM"
            );

            recommendation.setRecommendedAction(
                    "Ask the customer to update their payment method."
            );

            recommendation.setReasoning(
                    "An expired payment method should be updated "
                            + "before another payment attempt."
            );

            return recommendation;
        }

        /*
         * Insufficient funds:
         * Retry later if attempts are still available.
         */
        if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(failureReason)) {

            if (previousAttempts < maxAttempts) {

                recommendation.setDiagnosis(
                        "The payment failed because of insufficient funds."
                );

                recommendation.setStrategy(
                        "RETRY_LATER"
                );

                recommendation.setConfidence(0.85);

                recommendation.setRiskLevel(
                        "LOW"
                );

                recommendation.setRecommendedAction(
                        "Retry the payment after a delay."
                );

                recommendation.setReasoning(
                        "Insufficient funds may be temporary, so a delayed "
                                + "retry may recover the payment."
                );

            } else {

                recommendation.setDiagnosis(
                        "The payment failed because of insufficient funds "
                                + "and the retry limit has been reached."
                );

                recommendation.setStrategy(
                        "MANUAL_REVIEW"
                );

                recommendation.setConfidence(0.95);

                recommendation.setRiskLevel(
                        "HIGH"
                );

                recommendation.setRecommendedAction(
                        "Stop automatic retries and escalate for manual review."
                );

                recommendation.setReasoning(
                        "The maximum number of recovery attempts has been reached."
                );
            }

            return recommendation;
        }

        /*
         * Generic retryable failure.
         */
        if (previousAttempts < maxAttempts) {

            recommendation.setDiagnosis(
                    "The payment failed for a potentially retryable reason."
            );

            recommendation.setStrategy(
                    "RETRY_LATER"
            );

            recommendation.setConfidence(0.70);

            recommendation.setRiskLevel(
                    "MEDIUM"
            );

            recommendation.setRecommendedAction(
                    "Retry the payment according to the configured retry policy."
            );

            recommendation.setReasoning(
                    "Gemini was unavailable, so the deterministic recovery "
                            + "fallback was used."
            );

            return recommendation;
        }

        /*
         * No retry attempts remaining.
         */
        recommendation.setDiagnosis(
                "The payment cannot be recovered automatically."
        );

        recommendation.setStrategy(
                "MANUAL_REVIEW"
        );

        recommendation.setConfidence(0.90);

        recommendation.setRiskLevel(
                "HIGH"
        );

        recommendation.setRecommendedAction(
                "Escalate the payment for manual review."
        );

        recommendation.setReasoning(
                "Gemini was unavailable and no automatic retry attempts remain."
        );

        return recommendation;
    }
}