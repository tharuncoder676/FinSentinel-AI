package com.finsentinel.simulation;

import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.risk.RiskDtos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SimulationDtos {

    private SimulationDtos() {}

    public record SimulationParameters(
            @Size(max = 160) String name,
            String scenarioType,
            String templateKey,
            @Min(7) @Max(365) Integer horizonDays,
            @DecimalMin("0.0") @DecimalMax("3.0") BigDecimal monthlyIncomeMultiplier,
            @Min(-30) @Max(90) Integer incomeDelayDays,
            @DecimalMin("0.0") @DecimalMax("3.0") BigDecimal essentialExpenseMultiplier,
            @DecimalMin("0.0") @DecimalMax("3.0") BigDecimal discretionaryExpenseMultiplier,
            @DecimalMin("0.0") @DecimalMax("3.0") BigDecimal savingsMultiplier,
            @DecimalMin("0.0") BigDecimal unexpectedExpenseAmount,
            LocalDate unexpectedExpenseDate,
            @Min(-30) @Max(60) Integer paymentDateShiftDays,
            Map<UUID, LocalDate> obligationDateOverrides,
            @DecimalMin("0.0") BigDecimal reserveThresholdOverride,
            Boolean clusterPaymentsTogether
    ) {
        public int effectiveHorizon() {
            return horizonDays != null ? horizonDays : 90;
        }
    }

    public record StressTestTemplateDto(
            String templateKey,
            String title,
            String description,
            SimulationParameters defaultParameters
    ) {}

    public record ReserveDepletionPoint(
            LocalDate date,
            BigDecimal baselineBalance,
            BigDecimal scenarioBalance,
            BigDecimal reserveThreshold
    ) {}

    public record SimulationResponse(
            UUID savedSimulationId,
            String name,
            String scenarioType,
            String templateKey,
            String modelVersion,
            boolean ledgerMutated,
            SimulationParameters parameters,
            ForecastDtos.ForecastResultDto baselineForecast,
            ForecastDtos.ForecastResultDto scenarioForecast,
            RiskDtos.RiskAssessmentDto riskBefore,
            RiskDtos.RiskAssessmentDto riskAfter,
            List<ReserveDepletionPoint> comparisonSeries,
            List<String> impactSummary,
            Instant generatedAt
    ) {}

    public record SavedScenarioSummary(
            UUID id,
            String name,
            String scenarioType,
            String templateKey,
            int horizonDays,
            SimulationParameters parameters,
            Instant updatedAt
    ) {}

    public record CompareRequest(
            List<SimulationParameters> cases
    ) {}
}
