package com.finsentinel.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.DeterministicEventForecastStrategy;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.ledger.*;
import com.finsentinel.payment.FinancialObligationEntity;
import com.finsentinel.payment.FinancialObligationRepository;
import com.finsentinel.profile.FinancialProfileEntity;
import com.finsentinel.profile.FinancialProfileRepository;
import com.finsentinel.risk.RiskDtos;
import com.finsentinel.risk.RiskEngineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class SimulationService {

    private final DeterministicEventForecastStrategy forecastStrategy;
    private final ForecastService forecastService;
    private final RiskEngineService riskEngineService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialObligationRepository obligationRepository;
    private final FinancialProfileRepository profileRepository;
    private final LedgerService ledgerService;
    private final ScenarioSimulationRepository simulationRepository;
    private final ScenarioResultRepository resultRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SimulationService(DeterministicEventForecastStrategy forecastStrategy,
                             ForecastService forecastService,
                             RiskEngineService riskEngineService,
                             AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             IncomeSourceRepository incomeSourceRepository,
                             ExpenseRepository expenseRepository,
                             SavingsGoalRepository savingsGoalRepository,
                             FinancialObligationRepository obligationRepository,
                             FinancialProfileRepository profileRepository,
                             LedgerService ledgerService,
                             ScenarioSimulationRepository simulationRepository,
                             ScenarioResultRepository resultRepository,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.forecastStrategy = forecastStrategy;
        this.forecastService = forecastService;
        this.riskEngineService = riskEngineService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.expenseRepository = expenseRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.obligationRepository = obligationRepository;
        this.profileRepository = profileRepository;
        this.ledgerService = ledgerService;
        this.simulationRepository = simulationRepository;
        this.resultRepository = resultRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<SimulationDtos.StressTestTemplateDto> listStressTestTemplates() {
        LocalDate shockDate = LocalDate.now().plusDays(10);
        return List.of(
                new SimulationDtos.StressTestTemplateDto(
                        "INCOME_REDUCED_20",
                        "Income Reduced by 20%",
                        "Simulates a 20% reduction across active income streams over the projection horizon.",
                        new SimulationDtos.SimulationParameters("Income -20%", "STRESS_TEST", "INCOME_REDUCED_20", 90,
                                new BigDecimal("0.80"), 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ZERO, null, 0, Map.of(), null, false)
                ),
                new SimulationDtos.StressTestTemplateDto(
                        "SALARY_DELAYED_15",
                        "Salary Delayed by 15 Days",
                        "Shifts upcoming income arrivals forward by 15 days without delaying contractual obligations.",
                        new SimulationDtos.SimulationParameters("Salary Delayed 15d", "STRESS_TEST", "SALARY_DELAYED_15", 90,
                                BigDecimal.ONE, 15, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ZERO, null, 0, Map.of(), null, false)
                ),
                new SimulationDtos.StressTestTemplateDto(
                        "UNEXPECTED_MEDICAL_EXPENSE",
                        "Unexpected Medical Expense",
                        "Introduces an unplanned emergency medical outflow of 35,000 within 10 days.",
                        new SimulationDtos.SimulationParameters("Medical Shock 35k", "STRESS_TEST", "UNEXPECTED_MEDICAL_EXPENSE", 90,
                                BigDecimal.ONE, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                new BigDecimal("35000.00"), shockDate, 0, Map.of(), null, false)
                ),
                new SimulationDtos.StressTestTemplateDto(
                        "ESSENTIAL_EXPENSES_INCREASE_25",
                        "Increase in Essential Expenses (+25%)",
                        "Models sustained inflation of 25% across essential recurring living expenses.",
                        new SimulationDtos.SimulationParameters("Essential Costs +25%", "STRESS_TEST", "ESSENTIAL_EXPENSES_INCREASE_25", 90,
                                BigDecimal.ONE, 0, new BigDecimal("1.25"), BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ZERO, null, 0, Map.of(), null, false)
                ),
                new SimulationDtos.StressTestTemplateDto(
                        "CLUSTERED_PAYMENTS",
                        "Several Payments Due Together",
                        "Clusters upcoming obligations onto a single window 7 days from now to test liquidity depth.",
                        new SimulationDtos.SimulationParameters("Clustered Payments", "STRESS_TEST", "CLUSTERED_PAYMENTS", 90,
                                BigDecimal.ONE, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ZERO, null, 0, Map.of(), null, true)
                ),
                new SimulationDtos.StressTestTemplateDto(
                        "TEMPORARY_INCOME_LOSS",
                        "Temporary Loss of Income",
                        "Models complete pause of income (0x) over the selected horizon while essential commitments continue.",
                        new SimulationDtos.SimulationParameters("Temporary Income Loss", "STRESS_TEST", "TEMPORARY_INCOME_LOSS", 90,
                                BigDecimal.ZERO, 0, BigDecimal.ONE, new BigDecimal("0.60"), BigDecimal.ZERO,
                                BigDecimal.ZERO, null, 0, Map.of(), null, false)
                )
        );
    }

    @Transactional(readOnly = true)
    public SimulationDtos.SimulationResponse simulate(UUID userId, SimulationDtos.SimulationParameters params, LocalDate startDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now();
        int horizon = params.effectiveHorizon();

        ForecastDtos.ForecastResultDto baseline = forecastService.generateForecast(userId, horizon, effectiveStart, false);
        ForecastDtos.ForecastResultDto scenario = buildScenarioForecast(userId, params, effectiveStart, horizon);

        RiskDtos.RiskInputSnapshot baseRiskSnap = riskEngineService.buildInputSnapshot(userId, effectiveStart);
        RiskDtos.RiskAssessmentDto riskBefore = riskEngineService.evaluateSyntheticSnapshot(baseRiskSnap);
        RiskDtos.RiskInputSnapshot modifiedRiskSnap = applyScenarioToRiskInput(baseRiskSnap, params, scenario);
        RiskDtos.RiskAssessmentDto riskAfter = riskEngineService.evaluateSyntheticSnapshot(modifiedRiskSnap);

        List<SimulationDtos.ReserveDepletionPoint> series = new ArrayList<>();
        for (int i = 0; i < baseline.dailyPoints().size(); i++) {
            ForecastDtos.DailyProjectionPoint b = baseline.dailyPoints().get(i);
            ForecastDtos.DailyProjectionPoint s = i < scenario.dailyPoints().size() ? scenario.dailyPoints().get(i) : b;
            series.add(new SimulationDtos.ReserveDepletionPoint(
                    b.date(), b.closingBalance(), s.closingBalance(), scenario.reserveThreshold()));
        }

        List<String> impactSummary = buildImpactSummary(baseline, scenario, riskBefore, riskAfter);

        return new SimulationDtos.SimulationResponse(
                null,
                params.name() != null && !params.name().isBlank() ? params.name() : "Ad-hoc Digital Twin Scenario",
                params.scenarioType() != null ? params.scenarioType() : "DIGITAL_TWIN",
                params.templateKey(),
                scenario.modelVersion(),
                false,
                params,
                baseline,
                scenario,
                riskBefore,
                riskAfter,
                series,
                impactSummary,
                Instant.now()
        );
    }

    @Transactional
    public SimulationDtos.SimulationResponse saveScenario(UserSessionPrincipal principal, SimulationDtos.SimulationParameters params) {
        SimulationDtos.SimulationResponse sim = simulate(principal.userId(), params, LocalDate.now());
        Instant now = Instant.now();

        ScenarioSimulationEntity entity = new ScenarioSimulationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(principal.userId());
        entity.setName(sim.name());
        entity.setScenarioType(sim.scenarioType());
        entity.setTemplateKey(sim.templateKey());
        entity.setParametersJson(toJson(params));
        entity.setHorizonDays(params.effectiveHorizon());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        simulationRepository.save(entity);

        ScenarioResultEntity res = new ScenarioResultEntity();
        res.setId(UUID.randomUUID());
        res.setSimulationId(entity.getId());
        res.setUserId(principal.userId());
        res.setModelVersion(sim.modelVersion());
        res.setBaselineMinBalance(sim.baselineForecast().minProjectedBalance());
        res.setScenarioMinBalance(sim.scenarioForecast().minProjectedBalance());
        res.setShortageDate(sim.scenarioForecast().shortageDate());
        res.setRecoveryDate(sim.scenarioForecast().recoveryDate());
        res.setRiskBefore(sim.riskBefore().displayScore());
        res.setRiskAfter(sim.riskAfter().displayScore());
        res.setResultJson(toJson(sim.impactSummary()));
        res.setGeneratedAt(now);
        resultRepository.save(res);

        auditService.record(principal.userId(), principal.role(), "SCENARIO_SAVED", "SCENARIO_SIMULATION", entity.getId().toString(), "SUCCESS", Map.of());

        return new SimulationDtos.SimulationResponse(
                entity.getId(), sim.name(), sim.scenarioType(), sim.templateKey(), sim.modelVersion(),
                false, sim.parameters(), sim.baselineForecast(), sim.scenarioForecast(),
                sim.riskBefore(), sim.riskAfter(), sim.comparisonSeries(), sim.impactSummary(), sim.generatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<SimulationDtos.SavedScenarioSummary> listSavedScenarios(UUID userId) {
        return simulationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(s -> new SimulationDtos.SavedScenarioSummary(
                        s.getId(), s.getName(), s.getScenarioType(), s.getTemplateKey(),
                        s.getHorizonDays(), fromJson(s.getParametersJson()), s.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SimulationDtos.SimulationResponse reopenSavedScenario(UUID userId, UUID scenarioId) {
        ScenarioSimulationEntity entity = simulationRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> ApiException.notFound("Saved scenario not found"));
        SimulationDtos.SimulationParameters params = fromJson(entity.getParametersJson());
        SimulationDtos.SimulationResponse sim = simulate(userId, params, LocalDate.now());
        return new SimulationDtos.SimulationResponse(
                entity.getId(), entity.getName(), entity.getScenarioType(), entity.getTemplateKey(),
                sim.modelVersion(), false, params, sim.baselineForecast(), sim.scenarioForecast(),
                sim.riskBefore(), sim.riskAfter(), sim.comparisonSeries(), sim.impactSummary(), sim.generatedAt()
        );
    }

    @Transactional
    public void deleteScenario(UUID userId, UUID scenarioId) {
        ScenarioSimulationEntity entity = simulationRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> ApiException.notFound("Saved scenario not found"));
        simulationRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<SimulationDtos.SimulationResponse> compareScenarios(UUID userId, List<SimulationDtos.SimulationParameters> cases) {
        if (cases == null || cases.isEmpty()) {
            throw ApiException.badRequest("NO_CASES", "Provide at least one scenario case to compare");
        }
        if (cases.size() > 5) {
            throw ApiException.badRequest("TOO_MANY_CASES", "At most 5 scenarios can be compared simultaneously");
        }
        LocalDate start = LocalDate.now();
        return cases.stream().map(c -> simulate(userId, c, start)).toList();
    }

    private ForecastDtos.ForecastResultDto buildScenarioForecast(UUID userId, SimulationDtos.SimulationParameters p, LocalDate start, int horizon) {
        LocalDate endDate = start.plusDays(horizon);
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

        BigDecimal reserve = p.reserveThresholdOverride() != null
                ? p.reserveThresholdOverride()
                : (profile != null && profile.getReserveThreshold() != null ? profile.getReserveThreshold() : new BigDecimal("15000.00"));

        BigDecimal incMult = p.monthlyIncomeMultiplier() != null ? p.monthlyIncomeMultiplier() : BigDecimal.ONE;
        int incDelay = p.incomeDelayDays() != null ? p.incomeDelayDays() : 0;
        BigDecimal essMult = p.essentialExpenseMultiplier() != null ? p.essentialExpenseMultiplier() : BigDecimal.ONE;
        BigDecimal discMult = p.discretionaryExpenseMultiplier() != null ? p.discretionaryExpenseMultiplier() : BigDecimal.ONE;
        BigDecimal savMult = p.savingsMultiplier() != null ? p.savingsMultiplier() : BigDecimal.ONE;
        int payShift = p.paymentDateShiftDays() != null ? p.paymentDateShiftDays() : 0;
        Map<UUID, LocalDate> overrides = p.obligationDateOverrides() != null ? p.obligationDateOverrides() : Map.of();

        List<ForecastDtos.CashFlowEvent> events = new ArrayList<>();
        List<String> extraAssumptions = new ArrayList<>();
        extraAssumptions.add("Simulation is isolated from live ledger records; no account balances or due dates were modified.");

        if (incMult.compareTo(BigDecimal.ZERO) > 0) {
            for (IncomeSourceEntity inc : incomes) {
                if (!inc.isActive()) continue;
                int day = inc.getExpectedDay() != null ? inc.getExpectedDay() : 1;
                LocalDate anchor = inc.getNextExpectedDate() != null
                        ? inc.getNextExpectedDate()
                        : ForecastService.clampDayOfMonth(YearMonth.from(start), day);
                if (anchor.isBefore(start)) {
                    anchor = ForecastService.clampDayOfMonth(YearMonth.from(start.plusMonths(1)), day);
                }
                for (LocalDate d : ForecastService.expandRecurrence(anchor, inc.getFrequency(), start, endDate, day)) {
                    LocalDate shifted = d.plusDays(incDelay);
                    if (!shifted.isBefore(start) && !shifted.isAfter(endDate)) {
                        events.add(new ForecastDtos.CashFlowEvent(
                                shifted, ForecastDtos.EventKind.INCOME, inc.getName() + (incDelay != 0 ? " (Delayed)" : ""),
                                inc.getAmount().multiply(incMult).setScale(2, RoundingMode.HALF_UP), true, false, inc.getId()));
                    }
                }
            }
            if (incomes.stream().noneMatch(IncomeSourceEntity::isActive)
                    && profile != null && profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate anchor = start.withDayOfMonth(1).plusMonths(1);
                for (LocalDate d : ForecastService.expandRecurrence(anchor, profile.getIncomeFrequency(), start, endDate, 1)) {
                    LocalDate shifted = d.plusDays(incDelay);
                    if (!shifted.isBefore(start) && !shifted.isAfter(endDate)) {
                        events.add(new ForecastDtos.CashFlowEvent(
                                shifted, ForecastDtos.EventKind.INCOME, "Profile Income (Simulated)",
                                profile.getMonthlyIncome().multiply(incMult).setScale(2, RoundingMode.HALF_UP), true, false, null));
                    }
                }
            }
        }

        for (ExpenseEntity exp : expenses) {
            if (!exp.isActive()) continue;
            BigDecimal mult = exp.isEssential() ? essMult : discMult;
            if (mult.compareTo(BigDecimal.ZERO) == 0) continue;
            LocalDate anchor = exp.getNextDueDate() != null ? exp.getNextDueDate() : start.plusDays(7);
            ForecastDtos.EventKind kind = exp.isEssential()
                    ? ForecastDtos.EventKind.EXPENSE_ESSENTIAL
                    : ForecastDtos.EventKind.EXPENSE_DISCRETIONARY;
            for (LocalDate d : ForecastService.expandRecurrence(anchor, exp.getFrequency(), start, endDate, anchor.getDayOfMonth())) {
                events.add(new ForecastDtos.CashFlowEvent(
                        d, kind, exp.getTitle(), exp.getAmount().multiply(mult).setScale(2, RoundingMode.HALF_UP).negate(), true, false, exp.getId()));
            }
        }

        if (savMult.compareTo(BigDecimal.ZERO) > 0) {
            for (SavingsGoalEntity g : goals) {
                if (!"ACTIVE".equals(g.getStatus()) || g.getMonthlyContribution().compareTo(BigDecimal.ZERO) <= 0) continue;
                LocalDate anchor = start.plusDays(5);
                for (LocalDate d : ForecastService.expandRecurrence(anchor, "MONTHLY", start, endDate, anchor.getDayOfMonth())) {
                    events.add(new ForecastDtos.CashFlowEvent(
                            d, ForecastDtos.EventKind.SAVINGS, "Goal: " + g.getName(),
                            g.getMonthlyContribution().multiply(savMult).setScale(2, RoundingMode.HALF_UP).negate(), true, false, g.getId()));
                }
            }
        }

        LocalDate clusterDate = start.plusDays(7);
        for (FinancialObligationEntity ob : obligations) {
            BigDecimal remaining = ob.getRemainingAmount();
            if ("PAID".equals(ob.getStatus()) || remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            ForecastDtos.EventKind kind = ob.isEssential()
                    ? ForecastDtos.EventKind.OBLIGATION_ESSENTIAL
                    : ForecastDtos.EventKind.OBLIGATION_DISCRETIONARY;
            LocalDate baseDue = overrides.getOrDefault(ob.getId(),
                    Boolean.TRUE.equals(p.clusterPaymentsTogether()) ? clusterDate : ob.getDueDate().plusDays(payShift));
            if (baseDue.isBefore(start)) baseDue = start;
            if (!baseDue.isAfter(endDate)) {
                events.add(new ForecastDtos.CashFlowEvent(
                        baseDue, kind, ob.getTitle() + " (Simulated)", remaining.negate(), true, ob.getDueDate().isBefore(start), ob.getId()));
            }
        }

        if (p.unexpectedExpenseAmount() != null && p.unexpectedExpenseAmount().compareTo(BigDecimal.ZERO) > 0) {
            LocalDate shockDate = p.unexpectedExpenseDate() != null ? p.unexpectedExpenseDate() : start.plusDays(10);
            if (shockDate.isBefore(start)) shockDate = start;
            events.add(new ForecastDtos.CashFlowEvent(
                    shockDate, ForecastDtos.EventKind.EXPENSE_DISCRETIONARY,
                    "Unexpected Expense Shock", p.unexpectedExpenseAmount().negate(), true, false, null));
            extraAssumptions.add("Injected unexpected one-time expense of " + p.unexpectedExpenseAmount() + " on " + shockDate + ".");
        }

        boolean insufficientData = accounts.isEmpty() && incomes.isEmpty() && expenses.isEmpty() && obligations.isEmpty()
                && (profile == null || (profile.getMonthlyIncome() == null && profile.getLiquidFunds() == null));

        return forecastStrategy.project(start, horizon, openingBalance, reserve, events, insufficientData, extraAssumptions);
    }

    private RiskDtos.RiskInputSnapshot applyScenarioToRiskInput(
            RiskDtos.RiskInputSnapshot base,
            SimulationDtos.SimulationParameters p,
            ForecastDtos.ForecastResultDto scenarioForecast) {
        BigDecimal incMult = p.monthlyIncomeMultiplier() != null ? p.monthlyIncomeMultiplier() : BigDecimal.ONE;
        BigDecimal essMult = p.essentialExpenseMultiplier() != null ? p.essentialExpenseMultiplier() : BigDecimal.ONE;
        BigDecimal discMult = p.discretionaryExpenseMultiplier() != null ? p.discretionaryExpenseMultiplier() : BigDecimal.ONE;
        BigDecimal unexpected = p.unexpectedExpenseAmount() != null ? p.unexpectedExpenseAmount() : BigDecimal.ZERO;

        BigDecimal modIncome = base.monthlyIncome().multiply(incMult).setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseEssential = base.monthlyEssentialExpenses();
        BigDecimal baseDisc = base.monthlyTotalExpenses().subtract(baseEssential).max(BigDecimal.ZERO);
        BigDecimal modEssential = baseEssential.multiply(essMult).setScale(2, RoundingMode.HALF_UP);
        BigDecimal modTotalExpenses = modEssential.add(baseDisc.multiply(discMult)).add(unexpected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal modLiquid = scenarioForecast.minProjectedBalance().max(BigDecimal.ZERO);

        BigDecimal stability = base.incomeStabilityAvg();
        if (p.incomeDelayDays() != null && p.incomeDelayDays() > 0 && stability != null) {
            stability = stability.subtract(new BigDecimal("0.20")).max(BigDecimal.ZERO);
        }

        return new RiskDtos.RiskInputSnapshot(
                base.asOfDate(),
                modLiquid,
                modIncome,
                modTotalExpenses,
                modEssential,
                base.monthlyObligationsTotal(),
                base.dueWithin14Days(),
                base.overdueCount(),
                stability,
                base.historicalMonthsCount(),
                base.hasAccounts(),
                base.hasObligationsData()
        );
    }

    private List<String> buildImpactSummary(ForecastDtos.ForecastResultDto base,
                                            ForecastDtos.ForecastResultDto scen,
                                            RiskDtos.RiskAssessmentDto riskBefore,
                                            RiskDtos.RiskAssessmentDto riskAfter) {
        List<String> lines = new ArrayList<>();
        BigDecimal minDelta = scen.minProjectedBalance().subtract(base.minProjectedBalance());
        lines.add("Minimum projected balance changes by " + minDelta + " (from " + base.minProjectedBalance() + " to " + scen.minProjectedBalance() + ").");
        lines.add("Educational risk score changes from " + riskBefore.displayScore() + " (" + riskBefore.category() + ") to " + riskAfter.displayScore() + " (" + riskAfter.category() + ").");
        if (scen.shortageDate() != null) {
            lines.add("Cash shortage (< 0) first projected on " + scen.shortageDate() + ".");
        } else if (scen.reserveBreachDate() != null) {
            lines.add("Reserve threshold breach first projected on " + scen.reserveBreachDate() + " (balance stays positive).");
        } else {
            lines.add("Projected balance stays above reserve threshold throughout the horizon.");
        }
        lines.add(scen.recoveryMessage());
        return lines;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private SimulationDtos.SimulationParameters fromJson(String json) {
        try {
            return objectMapper.readValue(json, SimulationDtos.SimulationParameters.class);
        } catch (Exception e) {
            return new SimulationDtos.SimulationParameters("Saved Scenario", "DIGITAL_TWIN", null, 90,
                    BigDecimal.ONE, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ZERO, null, 0, Map.of(), null, false);
        }
    }
}
