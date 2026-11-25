package com.finsentinel.simulation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "scenario_results")
public class ScenarioResultEntity {
    @Id
    private UUID id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @Column(name = "baseline_min_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal baselineMinBalance;

    @Column(name = "scenario_min_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal scenarioMinBalance;

    @Column(name = "shortage_date")
    private LocalDate shortageDate;

    @Column(name = "recovery_date")
    private LocalDate recoveryDate;

    @Column(name = "risk_before")
    private Integer riskBefore;

    @Column(name = "risk_after")
    private Integer riskAfter;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSimulationId() { return simulationId; }
    public void setSimulationId(UUID simulationId) { this.simulationId = simulationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public BigDecimal getBaselineMinBalance() { return baselineMinBalance; }
    public void setBaselineMinBalance(BigDecimal baselineMinBalance) { this.baselineMinBalance = baselineMinBalance; }
    public BigDecimal getScenarioMinBalance() { return scenarioMinBalance; }
    public void setScenarioMinBalance(BigDecimal scenarioMinBalance) { this.scenarioMinBalance = scenarioMinBalance; }
    public LocalDate getShortageDate() { return shortageDate; }
    public void setShortageDate(LocalDate shortageDate) { this.shortageDate = shortageDate; }
    public LocalDate getRecoveryDate() { return recoveryDate; }
    public void setRecoveryDate(LocalDate recoveryDate) { this.recoveryDate = recoveryDate; }
    public Integer getRiskBefore() { return riskBefore; }
    public void setRiskBefore(Integer riskBefore) { this.riskBefore = riskBefore; }
    public Integer getRiskAfter() { return riskAfter; }
    public void setRiskAfter(Integer riskAfter) { this.riskAfter = riskAfter; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
