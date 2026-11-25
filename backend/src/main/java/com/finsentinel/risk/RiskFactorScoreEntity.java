package com.finsentinel.risk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "risk_factor_scores")
public class RiskFactorScoreEntity {
    @Id
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "factor_key", nullable = false, length = 48)
    private String factorKey;

    @Column(name = "factor_label", nullable = false, length = 120)
    private String factorLabel;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "normalized_value", precision = 7, scale = 4)
    private BigDecimal normalizedValue;

    @Column(name = "weighted_contribution", precision = 7, scale = 4)
    private BigDecimal weightedContribution;

    @Column(name = "measured_value", length = 120)
    private String measuredValue;

    @Column(length = 40)
    private String unit;

    @Column(name = "data_available", nullable = false)
    private boolean dataAvailable;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public String getFactorKey() { return factorKey; }
    public void setFactorKey(String factorKey) { this.factorKey = factorKey; }
    public String getFactorLabel() { return factorLabel; }
    public void setFactorLabel(String factorLabel) { this.factorLabel = factorLabel; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(BigDecimal normalizedValue) { this.normalizedValue = normalizedValue; }
    public BigDecimal getWeightedContribution() { return weightedContribution; }
    public void setWeightedContribution(BigDecimal weightedContribution) { this.weightedContribution = weightedContribution; }
    public String getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(String measuredValue) { this.measuredValue = measuredValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
