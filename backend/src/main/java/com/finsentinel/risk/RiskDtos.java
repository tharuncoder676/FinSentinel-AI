package com.finsentinel.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RiskDtos {

    private RiskDtos() {}

    public record RiskFactorDto(
            String factorKey,
            String factorLabel,
            BigDecimal weight,
            BigDecimal normalizedValue,
            BigDecimal weightedContribution,
            String measuredValue,
            String unit,
            String window,
            String normalizationFormula,
            boolean dataAvailable,
            String explanation
    ) {}

    public record RiskAssessmentDto(
            UUID id,
            String policyVersion,
            BigDecimal rawScore,
            int displayScore,
            String category,
            BigDecimal dataCompleteness,
            boolean insufficientData,
            String disclaimer,
            List<String> assumptions,
            List<RiskFactorDto> factors,
            Instant calculatedAt
    ) {}

    public record RiskHistoryPoint(
            UUID id,
            String policyVersion,
            int displayScore,
            BigDecimal rawScore,
            String category,
            BigDecimal dataCompleteness,
            Instant calculatedAt
    ) {}

    public record RiskInputSnapshot(
            LocalDate asOfDate,
            BigDecimal availableLiquidBalance,
            BigDecimal monthlyIncome,
            BigDecimal monthlyTotalExpenses,
            BigDecimal monthlyEssentialExpenses,
            BigDecimal monthlyObligationsTotal,
            BigDecimal dueWithin14Days,
            int overdueCount,
            BigDecimal incomeStabilityAvg,
            int historicalMonthsCount,
            boolean hasAccounts,
            boolean hasObligationsData
    ) {}
}
