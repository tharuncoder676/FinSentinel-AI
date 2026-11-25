package com.finsentinel.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {}

    public record ObligationDto(
            UUID id,
            UUID accountId,
            String title,
            String category,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            LocalDate dueDate,
            String frequency,
            boolean essential,
            String status,
            BigDecimal lateFee,
            int graceDays,
            BigDecimal interestRate,
            String notes,
            int priorityRank,
            String affordabilityStatus,
            BigDecimal estimatedCashFlowImpact
    ) {}

    public record ObligationRequest(
            UUID accountId,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 64) String category,
            @NotNull @DecimalMin("0.01") BigDecimal totalAmount,
            @NotNull LocalDate dueDate,
            @Pattern(regexp = "ONE_TIME|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|ANNUAL") String frequency,
            boolean essential,
            @DecimalMin("0.00") BigDecimal lateFee,
            @Min(0) @Max(60) Integer graceDays,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal interestRate,
            @Size(max = 500) String notes
    ) {}

    public record RecordPaymentRequest(
            UUID accountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotBlank @Pattern(regexp = "UPI|DEBIT_CARD|CREDIT_CARD|NET_BANKING|EMI|CASH|MANUAL") String paymentMethod,
            @Size(max = 255) String referenceNote,
            boolean recordLedgerTransaction
    ) {}

    public record PaymentRecordDto(
            UUID id,
            UUID obligationId,
            UUID accountId,
            UUID transactionId,
            BigDecimal amount,
            LocalDate paidOn,
            String paymentMethod,
            String referenceNote,
            Instant createdAt
    ) {}

    public record ScheduledObligationItem(
            UUID obligationId,
            String title,
            LocalDate contractualDueDate,
            LocalDate hypotheticalPayDate,
            boolean shiftedInSimulation,
            boolean essential,
            BigDecimal amount,
            BigDecimal projectedBalanceAfter,
            BigDecimal penaltyRisk,
            String note
    ) {}

    public record ScenarioScheduleDto(
            String strategy,
            String scenarioName,
            boolean feasible,
            boolean respectsReserveThreshold,
            BigDecimal shortfallAmount,
            BigDecimal minProjectedBalance,
            BigDecimal totalLateFeesAssumed,
            List<String> consequences,
            List<ScheduledObligationItem> items
    ) {}

    public record PaymentPlanComparisonResponse(
            BigDecimal availableBalance,
            BigDecimal reserveThreshold,
            int horizonDays,
            String advisoryNotice,
            List<ScenarioScheduleDto> scenarios
    ) {}

    public record SimulateRescheduleRequest(
            Map<UUID, LocalDate> hypotheticalDates,
            @Min(7) @Max(180) Integer horizonDays
    ) {}

    public record EmiRequest(
            @NotNull @DecimalMin("1.00") BigDecimal principal,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal annualInterestRatePercent,
            @NotNull @Min(1) @Max(360) Integer tenureMonths,
            @DecimalMin("0.00") BigDecimal processingFee,
            @Pattern(regexp = "FIXED_AMORTIZING|ZERO_INTEREST|VARIABLE_RATE") String rateModel
    ) {}

    public record AmortizationRow(
            int installmentNumber,
            BigDecimal openingPrincipal,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal closingPrincipal
    ) {}

    public record EmiResultDto(
            boolean supported,
            String unsupportedReason,
            String rateModel,
            BigDecimal principal,
            BigDecimal annualInterestRatePercent,
            int tenureMonths,
            BigDecimal processingFee,
            BigDecimal monthlyEmi,
            BigDecimal totalInterest,
            BigDecimal totalPayableWithFee,
            BigDecimal reconciledPrincipalSum,
            List<String> assumptions,
            List<AmortizationRow> schedule
    ) {}

    public record MethodComparisonInput(
            @NotNull @DecimalMin("1.00") BigDecimal purchaseAmount,
            @DecimalMin("0.00") BigDecimal upiFee,
            @DecimalMin("0.00") BigDecimal debitCardFee,
            @DecimalMin("0.00") BigDecimal creditCardFee,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal creditCardMonthlyCarryRatePercent,
            @Min(1) @Max(12) Integer creditCardMonthsToPay,
            @DecimalMin("0.00") BigDecimal netBankingFee,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal emiAnnualInterestPercent,
            @Min(1) @Max(60) Integer emiTenureMonths,
            @DecimalMin("0.00") BigDecimal emiProcessingFee
    ) {}

    public record PaymentMethodOptionDto(
            String method,
            String label,
            BigDecimal feeOrInterestCost,
            BigDecimal totalEstimatedCost,
            BigDecimal immediateCashFlowOutflow,
            BigDecimal futureMonthlyObligation,
            String affordabilityImpact,
            String assumptions
    ) {}

    public record MethodComparisonResponse(
            BigDecimal purchaseAmount,
            BigDecimal currentAvailableBalance,
            BigDecimal reserveThreshold,
            String disclaimer,
            List<PaymentMethodOptionDto> options,
            EmiResultDto emiDetails
    ) {}
}
