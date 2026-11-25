package com.finsentinel.profile;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_profiles")
public class FinancialProfileEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "monthly_income", precision = 19, scale = 4)
    private BigDecimal monthlyIncome;

    @Column(name = "other_income", precision = 19, scale = 4)
    private BigDecimal otherIncome;

    @Column(name = "regular_expenses", precision = 19, scale = 4)
    private BigDecimal regularExpenses;

    @Column(name = "liquid_funds", precision = 19, scale = 4)
    private BigDecimal liquidFunds;

    @Column(name = "reserve_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal reserveThreshold = new BigDecimal("15000.0000");

    @Column(name = "income_frequency", nullable = false, length = 32)
    private String incomeFrequency = "MONTHLY";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(name = "onboarding_skipped", nullable = false)
    private boolean onboardingSkipped = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(BigDecimal monthlyIncome) { this.monthlyIncome = monthlyIncome; }
    public BigDecimal getOtherIncome() { return otherIncome; }
    public void setOtherIncome(BigDecimal otherIncome) { this.otherIncome = otherIncome; }
    public BigDecimal getRegularExpenses() { return regularExpenses; }
    public void setRegularExpenses(BigDecimal regularExpenses) { this.regularExpenses = regularExpenses; }
    public BigDecimal getLiquidFunds() { return liquidFunds; }
    public void setLiquidFunds(BigDecimal liquidFunds) { this.liquidFunds = liquidFunds; }
    public BigDecimal getReserveThreshold() { return reserveThreshold; }
    public void setReserveThreshold(BigDecimal reserveThreshold) { this.reserveThreshold = reserveThreshold; }
    public String getIncomeFrequency() { return incomeFrequency; }
    public void setIncomeFrequency(String incomeFrequency) { this.incomeFrequency = incomeFrequency; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
    public boolean isOnboardingSkipped() { return onboardingSkipped; }
    public void setOnboardingSkipped(boolean onboardingSkipped) { this.onboardingSkipped = onboardingSkipped; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
