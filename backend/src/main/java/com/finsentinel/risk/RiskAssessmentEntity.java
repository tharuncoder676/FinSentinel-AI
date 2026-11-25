package com.finsentinel.risk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Column(name = "raw_score", nullable = false, precision = 7, scale = 4)
    private BigDecimal rawScore;

    @Column(name = "display_score", nullable = false)
    private int displayScore;

    @Column(nullable = false, length = 24)
    private String category;

    @Column(name = "data_completeness", nullable = false, precision = 5, scale = 4)
    private BigDecimal dataCompleteness;

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT")
    private String assumptionsJson;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public BigDecimal getRawScore() { return rawScore; }
    public void setRawScore(BigDecimal rawScore) { this.rawScore = rawScore; }
    public int getDisplayScore() { return displayScore; }
    public void setDisplayScore(int displayScore) { this.displayScore = displayScore; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(BigDecimal dataCompleteness) { this.dataCompleteness = dataCompleteness; }
    public boolean isInsufficientData() { return insufficientData; }
    public void setInsufficientData(boolean insufficientData) { this.insufficientData = insufficientData; }
    public String getAssumptionsJson() { return assumptionsJson; }
    public void setAssumptionsJson(String assumptionsJson) { this.assumptionsJson = assumptionsJson; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }
}
