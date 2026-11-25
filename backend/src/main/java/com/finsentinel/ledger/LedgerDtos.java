package com.finsentinel.ledger;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LedgerDtos {

    private LedgerDtos() {}

    public record AccountDto(
            UUID id,
            String name,
            String accountType,
            String currency,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate,
            BigDecimal currentBalance,
            boolean archived,
            Instant updatedAt
    ) {}

    public record AccountRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "CHECKING|SAVINGS|CASH|CREDIT_CARD") String accountType,
            @Size(min = 3, max = 8) String currency,
            @NotNull BigDecimal openingBalance,
            @NotNull LocalDate openingBalanceDate
    ) {}

    public record CategoryDto(
            UUID id,
            String name,
            String kind,
            boolean essential,
            String colorToken,
            boolean system
    ) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "INCOME|EXPENSE|SAVINGS|TRANSFER|REFUND") String kind,
            boolean essential,
            @Size(max = 32) String colorToken
    ) {}

    public record IncomeSourceDto(
            UUID id,
            UUID accountId,
            String name,
            BigDecimal amount,
            String frequency,
            Integer expectedDay,
            LocalDate nextExpectedDate,
            BigDecimal stabilityScore,
            boolean active
    ) {}

    public record IncomeSourceRequest(
            UUID accountId,
            @NotBlank @Size(max = 140) String name,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "MONTHLY|BIWEEKLY|WEEKLY|ONE_TIME") String frequency,
            @Min(1) @Max(31) Integer expectedDay,
            LocalDate nextExpectedDate,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal stabilityScore,
            Boolean active
    ) {}

    public record TransactionDto(
            UUID id,
            UUID accountId,
            String accountName,
            UUID transferAccountId,
            UUID categoryId,
            String categoryName,
            String txType,
            BigDecimal amount,
            String currency,
            LocalDate occurredOn,
            String merchant,
            String description,
            boolean essential,
            boolean recurring,
            String recurrenceRule,
            String anomalyStatus,
            UUID suggestedCategoryId,
            UUID linkedPaymentRecordId,
            boolean duplicateDetected
    ) {}

    public record TransactionRequest(
            @NotNull UUID accountId,
            UUID transferAccountId,
            UUID categoryId,
            @NotBlank @Pattern(regexp = "INCOME|EXPENSE|TRANSFER|REFUND|SAVINGS") String txType,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(min = 3, max = 8) String currency,
            @NotNull LocalDate occurredOn,
            @Size(max = 160) String merchant,
            @Size(max = 500) String description,
            Boolean essential,
            Boolean recurring,
            @Pattern(regexp = "NONE|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY") String recurrenceRule,
            Boolean allowDuplicate
    ) {}

    public record ExpenseDto(
            UUID id,
            UUID categoryId,
            String title,
            BigDecimal amount,
            String frequency,
            boolean essential,
            LocalDate nextDueDate,
            boolean active
    ) {}

    public record ExpenseRequest(
            UUID categoryId,
            @NotBlank @Size(max = 160) String title,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "MONTHLY|WEEKLY|QUARTERLY|ANNUAL|ONE_TIME") String frequency,
            boolean essential,
            LocalDate nextDueDate,
            Boolean active
    ) {}

    public record SavingsGoalDto(
            UUID id,
            String name,
            BigDecimal targetAmount,
            BigDecimal currentAmount,
            LocalDate targetDate,
            BigDecimal monthlyContribution,
            int priority,
            String status,
            double progressPercent
    ) {}

    public record SavingsGoalRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("1.00") BigDecimal targetAmount,
            @DecimalMin("0.00") BigDecimal currentAmount,
            LocalDate targetDate,
            @DecimalMin("0.00") BigDecimal monthlyContribution,
            @Min(1) @Max(10) Integer priority,
            @Pattern(regexp = "ACTIVE|PAUSED|COMPLETED") String status
    ) {}

    public record LedgerSummary(
            BigDecimal totalAvailableBalance,
            BigDecimal periodIncome,
            BigDecimal periodExpenses,
            BigDecimal periodSavings,
            BigDecimal previousPeriodIncome,
            BigDecimal previousPeriodExpenses,
            boolean hasPreviousComparableData,
            List<AccountDto> accounts
    ) {}
}
