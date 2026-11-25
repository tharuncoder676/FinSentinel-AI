package com.finsentinel.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ForecastDtos {

    private ForecastDtos() {}

    public enum EventKind {
        INCOME,
        SAVINGS,
        OBLIGATION_ESSENTIAL,
        OBLIGATION_DISCRETIONARY,
        EXPENSE_ESSENTIAL,
        EXPENSE_DISCRETIONARY
    }

    public record CashFlowEvent(
            LocalDate date,
            EventKind kind,
            String title,
            BigDecimal signedAmount,
            boolean projected,
            boolean overdue,
            UUID sourceId
    ) {}

    public record DailyProjectionPoint(
            LocalDate date,
            BigDecimal openingBalance,
            BigDecimal totalCredits,
            BigDecimal totalDebits,
            BigDecimal closingBalance,
            boolean shortfall,
            boolean reserveBreach,
            List<CashFlowEvent> events
    ) {}

    public record AffectedObligationDto(
            UUID obligationId,
            String title,
            LocalDate dueDate,
            BigDecimal remainingAmount,
            BigDecimal projectedBalanceOnDueDate,
            boolean causesShortfall,
            boolean breachesReserve
    ) {}

    public record ForecastResultDto(
            String modelVersion,
            LocalDate startDate,
            int horizonDays,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            BigDecimal minProjectedBalance,
            LocalDate minBalanceDate,
            LocalDate shortageDate,
            LocalDate reserveBreachDate,
            LocalDate recoveryDate,
            String recoveryMessage,
            boolean insufficientData,
            List<String> assumptions,
            List<DailyProjectionPoint> dailyPoints,
            List<AffectedObligationDto> affectedObligations,
            Instant generatedAt
    ) {}
}
