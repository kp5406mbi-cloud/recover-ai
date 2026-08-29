package com.recoverai.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recovery_decisions")
public class RecoveryDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private String strategy;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Double confidence;

    /*
     * Structured diagnosis produced by the recovery decision engine.
     * Example: "Transient payment failure caused by insufficient funds"
     */
    private String diagnosis;

    /*
     * Human-readable action selected by the decision engine.
     * Example: "Retry payment after 24 hours"
     */
    private String recommendedAction;

    /*
     * LOW / MEDIUM / HIGH / CRITICAL
     */
    private String riskLevel;

    /*
     * Indicates whether the selected action is permitted
     * by the recovery policy.
     */
    private String policyStatus;

    /*
     * Delay before the next retry, in seconds.
     */
    private Long retryDelaySeconds;

    /*
     * Maximum number of attempts permitted for this failure type.
     */
    private Integer maxAttempts;

    @Column(nullable = false, updatable = false)
    private Instant recommendedAt;

    private Instant executedAt;

    private String outcome;

    @PrePersist
    protected void onCreate() {
        recommendedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getPolicyStatus() {
        return policyStatus;
    }

    public void setPolicyStatus(String policyStatus) {
        this.policyStatus = policyStatus;
    }

    public Long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(Long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getRecommendedAt() {
        return recommendedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}