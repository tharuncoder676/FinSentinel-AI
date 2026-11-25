package com.finsentinel.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.ledger.*;
import com.finsentinel.payment.FinancialObligationEntity;
import com.finsentinel.payment.FinancialObligationRepository;
import com.finsentinel.profile.FinancialProfileEntity;
import com.finsentinel.profile.FinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class ForecastService {

    private final DeterministicEventForecastStrategy forecastStrategy;
    private final ForecastSnapshotRepository snapshotRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialObligationRepository obligationRepository;
    private final FinancialProfileRepository profileRepository;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public ForecastService(DeterministicEventForecastStrategy forecastStrategy,
                           ForecastSnapshotRepository snapshotRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           IncomeSourceRepository incomeSourceRepository,
                           ExpenseRepository expenseRepository,
                           SavingsGoalRepository savingsGoalRepository,
                           FinancialObligationRepository obligationRepository,
                           FinancialProfileRepository profileRepository,
                           LedgerService ledgerService,
                           ObjectMapper objectMapper) {
        this.forecastStrategy = forecastStrategy;
        this.snapshotRepository = snapshotRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.expenseRepository = expenseRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.obligationRepository = obligationRepository;
        this.profileRepository = profileRepository;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ForecastDtos.ForecastResultDto generateForecast(UUID userId, int horizonDays, LocalDate startDate, boolean persistSnapshot) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now();
        int safeHorizon = Math.max(7, Math.min(365, horizonDays));
        LocalDate endDate = effectiveStart.plusDays(safeHorizon);

        List<AccountEntity> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<TransactionEntity> allTxs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        List<IncomeSourceEntity> incomes = incomeSourceRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<ExpenseEntity> expenses = expenseRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<SavingsGoalEntity> goals = savingsGoalRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);
        List<FinancialObligationEntity> obligations = obligationRepository.findByUserIdOrderByDueDateAsc(userId);
        FinancialProfileEntity profile = profileRepository.findById(userId).orElse(null);

        BigDecimal openingBalance = BigDecimal.ZERO;
        for (AccountEntity acc : accounts) {
            if (!"CREDIT_CARD".equals(acc.getAccountType())) {
                openingBalance = openingBalance.add(ledgerService.computeAccountBalance(acc, allTxs));
            }
        }
        if (accounts.isEmpty() && profile != null && profile.getLiquidFunds() != null) {
            openingBalance = profile.getLiquidFunds();
        }

        BigDecimal reserveThreshold = profile != null && profile.getReserveThreshold() != null
                ? profile.getReserveThreshold() : new BigDecimal("15000.00");

        List<String> extraAssumptions = new ArrayList<>();
        List<ForecastDtos.CashFlowEvent> events = new ArrayList<>();

        // 1. Income events
        for (IncomeSourceEntity inc : incomes) {
            if (!inc.isActive()) continue;
            LocalDate anchor = inc.getNextExpectedDate();
            if (anchor == null) {
                int day = inc.getExpectedDay() != null ? inc.getExpectedDay() : 1;
                anchor = clampDayOfMonth(YearMonth.from(effectiveStart), day);
                if (anchor.isBefore(effectiveStart)) {
                    anchor = clampDayOfMonth(YearMonth.from(effectiveStart.plusMonths(1)), day);
                }
                extraAssumptions.add("Income '" + inc.getName() + "' missing next expected date; inferred day " + day + ".");
            }
            for (LocalDate d : expandRecurrence(anchor, inc.getFrequency(), effectiveStart, endDate, inc.getExpectedDay())) {
                events.add(new ForecastDtos.CashFlowEvent(
                        d, ForecastDtos.EventKind.INCOME, inc.getName(), inc.getAmount(), true, false, inc.getId()));
            }
        }

        // Fallback to profile income if no active income source records exist
        if (incomes.stream().noneMatch(IncomeSourceEntity::isActive)
                && profile != null && profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            LocalDate anchor = effectiveStart.withDayOfMonth(1).plusMonths(1);
            for (LocalDate d : expandRecurrence(anchor, profile.getIncomeFrequency(), effectiveStart, endDate, 1)) {
                events.add(new ForecastDtos.CashFlowEvent(
                        d, ForecastDtos.EventKind.INCOME, "Profile Baseline Income", profile.getMonthlyIncome(), true, false, null));
            }
            extraAssumptions.add("Using onboarding profile income because no individual income source records are configured.");
        }

        // 2. Recurring expense templates
        for (ExpenseEntity exp : expenses) {
            if (!exp.isActive()) continue;
            LocalDate anchor = exp.getNextDueDate() != null ? exp.getNextDueDate() : effectiveStart.plusDays(7);
            ForecastDtos.EventKind kind = exp.isEssential()
                    ? ForecastDtos.EventKind.EXPENSE_ESSENTIAL
                    : ForecastDtos.EventKind.EXPENSE_DISCRETIONARY;
            for (LocalDate d : expandRecurrence(anchor, exp.getFrequency(), effectiveStart, endDate, anchor.getDayOfMonth())) {
                events.add(new ForecastDtos.CashFlowEvent(
                        d, kind, exp.getTitle(), exp.getAmount().negate(), true, false, exp.getId()));
            }
        }

        // 3. Savings goal monthly contributions
        for (SavingsGoalEntity g : goals) {
            if (!"ACTIVE".equals(g.getStatus()) || g.getMonthlyContribution().compareTo(BigDecimal.ZERO) <= 0) continue;
            LocalDate anchor = effectiveStart.plusDays(5);
            for (LocalDate d : expandRecurrence(anchor, "MONTHLY", effectiveStart, endDate, anchor.getDayOfMonth())) {
                events.add(new ForecastDtos.CashFlowEvent(
                        d, ForecastDtos.EventKind.SAVINGS, "Goal: " + g.getName(), g.getMonthlyContribution().negate(), true, false, g.getId()));
            }
        }

        // 4. Financial obligations (only unpaid remainder, exact once per due occurrence)
        for (FinancialObligationEntity ob : obligations) {
            BigDecimal remaining = ob.getRemainingAmount();
            if ("PAID".equals(ob.getStatus()) || remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            ForecastDtos.EventKind kind = ob.isEssential()
                    ? ForecastDtos.EventKind.OBLIGATION_ESSENTIAL
                    : ForecastDtos.EventKind.OBLIGATION_DISCRETIONARY;

            if (ob.getDueDate().isBefore(effectiveStart)) {
                BigDecimal withLateFee = remaining.add(ob.getLateFee() != null ? ob.getLateFee() : BigDecimal.ZERO);
                events.add(new ForecastDtos.CashFlowEvent(
                        effectiveStart, kind, ob.getTitle() + " (Overdue)", withLateFee.negate(), true, true, ob.getId()));
                if (!"ONE_TIME".equals(ob.getFrequency())) {
                    LocalDate nextCycle = stepDate(ob.getDueDate(), ob.getFrequency(), ob.getDueDate().getDayOfMonth());
                    while (nextCycle.isBefore(effectiveStart)) {
                        nextCycle = stepDate(nextCycle, ob.getFrequency(), ob.getDueDate().getDayOfMonth());
                    }
                    for (LocalDate d : expandRecurrence(nextCycle, ob.getFrequency(), effectiveStart, endDate, ob.getDueDate().getDayOfMonth())) {
                        events.add(new ForecastDtos.CashFlowEvent(
                                d, kind, ob.getTitle(), ob.getTotalAmount().negate(), true, false, ob.getId()));
                    }
                }
            } else {
                List<LocalDate> dates = expandRecurrence(ob.getDueDate(), ob.getFrequency(), effectiveStart, endDate, ob.getDueDate().getDayOfMonth());
                for (int i = 0; i < dates.size(); i++) {
                    BigDecimal amt = (i == 0) ? remaining : ob.getTotalAmount();
                    events.add(new ForecastDtos.CashFlowEvent(
                            dates.get(i), kind, ob.getTitle(), amt.negate(), true, false, ob.getId()));
                }
            }
        }

        boolean insufficientData = accounts.isEmpty() && incomes.isEmpty() && expenses.isEmpty() && obligations.isEmpty()
                && (profile == null || (profile.getMonthlyIncome() == null && profile.getLiquidFunds() == null));

        ForecastDtos.ForecastResultDto result = forecastStrategy.project(
                effectiveStart, safeHorizon, openingBalance, reserveThreshold, events, insufficientData, extraAssumptions);

        if (persistSnapshot && !insufficientData) {
            ForecastSnapshotEntity snap = new ForecastSnapshotEntity();
            snap.setId(UUID.randomUUID());
            snap.setUserId(userId);
            snap.setModelVersion(result.modelVersion());
            snap.setHorizonDays(result.horizonDays());
            snap.setOpeningBalance(result.openingBalance());
            snap.setReserveThreshold(result.reserveThreshold());
            snap.setMinProjectedBalance(result.minProjectedBalance());
            snap.setShortageDate(result.shortageDate());
            snap.setReserveBreachDate(result.reserveBreachDate());
            snap.setSeriesJson(toJson(result.dailyPoints()));
            snap.setAssumptionsJson(toJson(result.assumptions()));
            snap.setGeneratedAt(Instant.now());
            snapshotRepository.save(snap);
        }

        return result;
    }

    public static List<LocalDate> expandRecurrence(LocalDate anchor, String frequency, LocalDate windowStart, LocalDate windowEnd, Integer preferredDay) {
        List<LocalDate> out = new ArrayList<>();
        if (anchor == null) return out;
        String freq = frequency != null ? frequency : "ONE_TIME";
        if ("ONE_TIME".equals(freq)) {
            if (!anchor.isBefore(windowStart) && !anchor.isAfter(windowEnd)) {
                out.add(anchor);
            }
            return out;
        }
        int targetDay = preferredDay != null ? preferredDay : anchor.getDayOfMonth();
        LocalDate cursor = anchor;
        int guard = 0;
        while (!cursor.isAfter(windowEnd) && guard < 400) {
            guard++;
            if (!cursor.isBefore(windowStart)) {
                out.add(cursor);
            }
            cursor = stepDate(cursor, freq, targetDay);
        }
        return out;
    }

    public static LocalDate stepDate(LocalDate current, String frequency, int preferredDay) {
        return switch (frequency != null ? frequency : "MONTHLY") {
            case "WEEKLY" -> current.plusWeeks(1);
            case "BIWEEKLY" -> current.plusWeeks(2);
            case "QUARTERLY" -> clampDayOfMonth(YearMonth.from(current.plusMonths(3)), preferredDay);
            case "ANNUAL" -> clampDayOfMonth(YearMonth.from(current.plusYears(1)), preferredDay);
            default -> clampDayOfMonth(YearMonth.from(current.plusMonths(1)), preferredDay);
        };
    }

    public static LocalDate clampDayOfMonth(YearMonth ym, int day) {
        int clamped = Math.max(1, Math.min(day, ym.lengthOfMonth()));
        return ym.atDay(clamped);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
