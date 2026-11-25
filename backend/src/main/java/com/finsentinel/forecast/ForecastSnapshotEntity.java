package com.finsentinel.forecast;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "forecast_snapshots")
public class ForecastSnapshotEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "reserve_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal reserveThreshold;

    @Column(name = "min_projected_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minProjectedBalance;

    @Column(name = "shortage_date")
    private LocalDate shortageDate;

    @Column(name = "reserve_breach_date")
    private LocalDate reserveBreachDate;

    @Column(name = "series_json", nullable = false, columnDefinition = "TEXT")
    private String seriesJson;

    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT")
    private String assumptionsJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public BigDecimal getReserveThreshold() { return reserveThreshold; }
    public void setReserveThreshold(BigDecimal reserveThreshold) { this.reserveThreshold = reserveThreshold; }
    public BigDecimal getMinProjectedBalance() { return minProjectedBalance; }
    public void setMinProjectedBalance(BigDecimal minProjectedBalance) { this.minProjectedBalance = minProjectedBalance; }
    public LocalDate getShortageDate() { return shortageDate; }
    public void setShortageDate(LocalDate shortageDate) { this.shortageDate = shortageDate; }
    public LocalDate getReserveBreachDate() { return reserveBreachDate; }
    public void setReserveBreachDate(LocalDate reserveBreachDate) { this.reserveBreachDate = reserveBreachDate; }
    public String getSeriesJson() { return seriesJson; }
    public void setSeriesJson(String seriesJson) { this.seriesJson = seriesJson; }
    public String getAssumptionsJson() { return assumptionsJson; }
    public void setAssumptionsJson(String assumptionsJson) { this.assumptionsJson = assumptionsJson; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
