package com.finsentinel.dashboard;

import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.ledger.LedgerDtos;
import com.finsentinel.ledger.LedgerService;
import com.finsentinel.ledger.TransactionEntity;
import com.finsentinel.ledger.TransactionRepository;
import com.finsentinel.payment.PaymentDtos;
import com.finsentinel.payment.PaymentPlannerService;
import com.finsentinel.risk.RiskDtos;
import com.finsentinel.risk.RiskEngineService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final LedgerService ledgerService;
    private final RiskEngineService riskEngineService;
    private final ForecastService forecastService;
    private final PaymentPlannerService paymentPlannerService;
    private final TransactionRepository transactionRepository;

    public DashboardController(LedgerService ledgerService,
                               RiskEngineService riskEngineService,
                               ForecastService forecastService,
                               PaymentPlannerService paymentPlannerService,
                               TransactionRepository transactionRepository) {
        this.ledgerService = ledgerService;
        this.riskEngineService = riskEngineService;
        this.forecastService = forecastService;
        this.paymentPlannerService = paymentPlannerService;
        this.transactionRepository = transactionRepository;
    }

    public record DailyChartSeriesPoint(
            LocalDate date,
            boolean projected,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal projectedBalance,
            BigDecimal reserveThreshold,
            boolean shortfall,
            boolean reserveBreach
    ) {}

    public record DeterministicInsightItem(
            String id,
            String severity,
            String title,
            String summary,
            String explanation,
            String sourceReference
    ) {}

    public record DashboardResponse(
            String period,
            Instant lastUpdatedAt,
            boolean emptyWorkspace,
            LedgerDtos.LedgerSummary ledgerSummary,
            RiskDtos.RiskAssessmentDto riskAssessment,
            ForecastDtos.ForecastResultDto forecast,
            List<DailyChartSeriesPoint> cashFlowChartSeries,
            List<PaymentDtos.ObligationDto> upcomingPayments,
            List<DeterministicInsightItem> insightFeed
    ) {}

    @GetMapping
    public DashboardResponse getDashboard(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestParam(defaultValue = "30d") String period) {

        int horizon = switch (period) {
            case "7d" -> 7;
            case "90d" -> 90;
            case "12m" -> 365;
            default -> 30;
        };

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(horizon);
        UUID userId = principal.userId();

        LedgerDtos.LedgerSummary ledger = ledgerService.computeSummary(userId, from, today);
        RiskDtos.RiskAssessmentDto risk = riskEngineService.getLatestOrCompute(userId);
        ForecastDtos.ForecastResultDto forecast = forecastService.generateForecast(userId, horizon, today, false);
        List<PaymentDtos.ObligationDto> obligations = paymentPlannerService.listObligations(userId);

        List<TransactionEntity> histTxs = transactionRepository.findByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(userId, from, today);
        Map<LocalDate, BigDecimal> histIn = new HashMap<>();
        Map<LocalDate, BigDecimal> histOut = new HashMap<>();
        for (TransactionEntity tx : histTxs) {
            if ("INCOME".equals(tx.getTxType())) histIn.merge(tx.getOccurredOn(), tx.getAmount(), BigDecimal::add);
            else if ("EXPENSE".equals(tx.getTxType())) histOut.merge(tx.getOccurredOn(), tx.getAmount(), BigDecimal::add);
        }

        List<DailyChartSeriesPoint> series = new ArrayList<>();
        // Historical portion
        for (LocalDate d = from; d.isBefore(today); d = d.plusDays(1)) {
            BigDecimal inc = histIn.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal exp = histOut.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (inc.compareTo(BigDecimal.ZERO) > 0 || exp.compareTo(BigDecimal.ZERO) > 0 || d.equals(from)) {
                series.add(new DailyChartSeriesPoint(d, false, inc, exp, null, forecast.reserveThreshold(), false, false));
            }
        }
        // Projected portion
        for (ForecastDtos.DailyProjectionPoint pt : forecast.dailyPoints()) {
            series.add(new DailyChartSeriesPoint(
                    pt.date(), true, pt.totalCredits(), pt.totalDebits(), pt.closingBalance(),
                    forecast.reserveThreshold(), pt.shortfall(), pt.reserveBreach()));
        }

        boolean emptyWorkspace = ledger.accounts().isEmpty() && histTxs.isEmpty() && obligations.isEmpty();
        List<DeterministicInsightItem> insights = buildDeterministicInsights(ledger, risk, forecast, obligations, emptyWorkspace);

        return new DashboardResponse(
                period,
                Instant.now(),
                emptyWorkspace,
                ledger,
                risk,
                forecast,
                series,
                obligations.stream().filter(o -> !"PAID".equals(o.status())).limit(6).toList(),
                insights
        );
    }

    private List<DeterministicInsightItem> buildDeterministicInsights(
            LedgerDtos.LedgerSummary ledger,
            RiskDtos.RiskAssessmentDto risk,
            ForecastDtos.ForecastResultDto forecast,
            List<PaymentDtos.ObligationDto> obligations,
            boolean emptyWorkspace) {
        List<DeterministicInsightItem> items = new ArrayList<>();
        if (emptyWorkspace) {
            items.add(new DeterministicInsightItem(
                    "empty-setup",
                    "INFO",
                    "Workspace awaiting financial records",
                    "Add an account, income source, or obligation to unlock personalized risk scoring and projections.",
                    "Deterministic engines require recorded balances and cash-flow items; zero values are never fabricated.",
                    "Financial Profile & Accounts"
            ));
            return items;
        }

        if (forecast.shortageDate() != null) {
            items.add(new DeterministicInsightItem(
                    "shortfall-projected",
                    "CRITICAL",
                    "Projected cash shortfall on " + forecast.shortageDate(),
                    "Minimum projected balance reaches " + forecast.minProjectedBalance() + " within the " + forecast.horizonDays() + "-day horizon.",
                    forecast.recoveryMessage() + " Review upcoming obligations or test scenarios in Digital Twin.",
                    "Forecast (" + forecast.modelVersion() + ")"
            ));
        } else if (forecast.reserveBreachDate() != null) {
            items.add(new DeterministicInsightItem(
                    "reserve-breach",
                    "WARNING",
                    "Reserve threshold breach projected on " + forecast.reserveBreachDate(),
                    "Balance dips below your " + forecast.reserveThreshold() + " reserve buffer (min " + forecast.minProjectedBalance() + "), though it stays positive.",
                    "Consider staggering non-essential expenses or reviewing the Payment Planner reserve-safe schedule.",
                    "Forecast (" + forecast.modelVersion() + ")"
            ));
        } else {
            items.add(new DeterministicInsightItem(
                    "liquidity-healthy",
                    "POSITIVE",
                    "Liquidity remains above reserve threshold",
                    "Minimum projected balance of " + forecast.minProjectedBalance() + " stays above your " + forecast.reserveThreshold() + " buffer.",
                    "All scheduled obligations and recurring expenses are covered across the " + forecast.horizonDays() + "-day forecast.",
                    "Forecast (" + forecast.modelVersion() + ")"
            ));
        }

        risk.factors().stream()
                .filter(f -> f.dataAvailable() && f.weightedContribution() != null)
                .max(Comparator.comparing(RiskDtos.RiskFactorDto::weightedContribution))
                .ifPresent(top -> items.add(new DeterministicInsightItem(
                        "top-risk-factor",
                        risk.displayScore() > 60 ? "WARNING" : "INFO",
                        "Primary risk driver: " + top.factorLabel(),
                        top.factorLabel() + " contributes " + top.weightedContribution() + " pts to your " + risk.displayScore() + "/100 score.",
                        top.explanation(),
                        "Risk Policy (" + risk.policyVersion() + ")"
                )));

        long overdueCount = obligations.stream().filter(o -> "OVERDUE".equals(o.status())).count();
        if (overdueCount > 0) {
            items.add(new DeterministicInsightItem(
                    "overdue-obligations",
                    "CRITICAL",
                    overdueCount + " overdue obligation" + (overdueCount > 1 ? "s" : "") + " require attention",
                    "Overdue commitments are included on day 0 of the cash-flow projection with any configured late fees.",
                    "Record a manual settlement once paid or review priority ordering in Payment Planner.",
                    "Payment Obligations"
            ));
        }

        if (ledger.periodIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal net = ledger.periodIncome().subtract(ledger.periodExpenses());
            items.add(new DeterministicInsightItem(
                    "period-net",
                    net.compareTo(BigDecimal.ZERO) >= 0 ? "POSITIVE" : "WARNING",
                    "Trailing period net cash flow: " + net,
                    "Recorded income of " + ledger.periodIncome() + " minus expenses of " + ledger.periodExpenses() + " (transfers excluded).",
                    "Transfers between tracked accounts are excluded from income/expense totals to preserve single-source-of-truth accounting.",
                    "Ledger Summary"
            ));
        }
        return items;
    }
}
