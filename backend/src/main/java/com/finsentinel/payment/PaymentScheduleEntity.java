package com.finsentinel.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_schedules")
public class PaymentScheduleEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "scenario_name", nullable = false, length = 140)
    private String scenarioName;

    @Column(nullable = false, length = 48)
    private String strategy;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(nullable = false)
    private boolean feasible;

    @Column(name = "shortfall_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shortfallAmount;

    @Column(name = "min_projected_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minProjectedBalance;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }
    public BigDecimal getShortfallAmount() { return shortfallAmount; }
    public void setShortfallAmount(BigDecimal shortfallAmount) { this.shortfallAmount = shortfallAmount; }
    public BigDecimal getMinProjectedBalance() { return minProjectedBalance; }
    public void setMinProjectedBalance(BigDecimal minProjectedBalance) { this.minProjectedBalance = minProjectedBalance; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
