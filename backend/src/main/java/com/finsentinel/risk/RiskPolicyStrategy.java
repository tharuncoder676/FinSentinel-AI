package com.finsentinel.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public interface RiskPolicyStrategy {
    String version();
    EvaluationResult evaluate(RiskDtos.RiskInputSnapshot input);

    record EvaluationResult(
            String policyVersion,
            BigDecimal rawScore,
            int displayScore,
            String category,
            BigDecimal dataCompleteness,
            boolean insufficientData,
            List<String> assumptions,
            List<RiskDtos.RiskFactorDto> factors
    ) {}
}

class StandardRiskPolicyV1 implements RiskPolicyStrategy {

    public static final String POLICY_VERSION = "risk-policy-v1.0";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Override
    public String version() {
        return POLICY_VERSION;
    }

    @Override
    public EvaluationResult evaluate(RiskDtos.RiskInputSnapshot input) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Educational vulnerability scale 0-100 (higher = higher estimated vulnerability). Illustrative thresholds: 0-30 Low, 31-60 Moderate, 61-80 High, 81-100 Critical.");
        assumptions.add("Integer display score uses HALF_UP rounding on the weighted sum.");

        List<RiskDtos.RiskFactorDto> factors = new ArrayList<>();

        boolean hasIncome = input.monthlyIncome() != null && input.monthlyIncome().compareTo(ZERO) > 0;
        boolean hasExpenses = input.monthlyTotalExpenses() != null && input.monthlyTotalExpenses().compareTo(ZERO) > 0;
        boolean hasEssential = input.monthlyEssentialExpenses() != null && input.monthlyEssentialExpenses().compareTo(ZERO) > 0;

        // 1. Debt payment burden (25%)
        BigDecimal wDebt = new BigDecimal("0.2500");
        RiskDtos.RiskFactorDto debtFactor;
        if (!hasIncome || !input.hasObligationsData()) {
            assumptions.add("Debt payment burden marked unavailable due to missing income or liability records.");
            debtFactor = new RiskDtos.RiskFactorDto(
                    "DEBT_PAYMENT_BURDEN", "Debt & Obligation Burden", wDebt, null, null,
                    "Unavailable", "% of monthly income", "Next 30 days",
                    "clamp((monthlyObligations / monthlyIncome) / 0.60, 0, 1)",
                    false, "Requires positive monthly income and recorded obligations to measure burden accurately."
            );
        } else {
            BigDecimal ratio = input.monthlyObligationsTotal().divide(input.monthlyIncome(), 4, RoundingMode.HALF_UP);
            BigDecimal norm = clamp01(ratio.divide(new BigDecimal("0.60"), 4, RoundingMode.HALF_UP));
            BigDecimal contrib = norm.multiply(wDebt).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            debtFactor = new RiskDtos.RiskFactorDto(
                    "DEBT_PAYMENT_BURDEN", "Debt & Obligation Burden", wDebt, norm, contrib,
                    ratio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%",
                    "% of monthly income", "Next 30 days",
                    "clamp((monthlyObligations / monthlyIncome) / 0.60, 0, 1)",
                    true,
                    "Monthly obligations total " + input.monthlyObligationsTotal().setScale(2, RoundingMode.HALF_UP)
                            + " against monthly income of " + input.monthlyIncome().setScale(2, RoundingMode.HALF_UP) + "."
            );
        }
        factors.add(debtFactor);

        // 2. Cash-flow deficit (25%)
        BigDecimal wDeficit = new BigDecimal("0.2500");
        RiskDtos.RiskFactorDto deficitFactor;
        if (!hasIncome) {
            assumptions.add("Cash-flow deficit cannot be normalized against zero or missing income.");
            deficitFactor = new RiskDtos.RiskFactorDto(
                    "CASH_FLOW_DEFICIT", "Cash-Flow Deficit Pressure", wDeficit, null, null,
                    "Unavailable", "% net cash-flow ratio", "Trailing 30d & 30d outlook",
                    "Surplus >=20% -> 0.0; Breakeven -> 0.35; Deficit >=30% -> 1.0",
                    false, "Add an income source or profile income to compute monthly net cash-flow ratio."
            );
        } else {
            BigDecimal outflow = (input.monthlyTotalExpenses() != null ? input.monthlyTotalExpenses() : ZERO)
                    .max(input.monthlyObligationsTotal() != null ? input.monthlyObligationsTotal() : ZERO);
            BigDecimal netRatio = input.monthlyIncome().subtract(outflow).divide(input.monthlyIncome(), 4, RoundingMode.HALF_UP);
            // Map netRatio from [-0.30, +0.20] -> [1.0, 0.0]
            BigDecimal norm;
            if (netRatio.compareTo(new BigDecimal("0.20")) >= 0) {
                norm = ZERO;
            } else if (netRatio.compareTo(new BigDecimal("-0.30")) <= 0) {
                norm = ONE;
            } else {
                norm = clamp01(new BigDecimal("0.20").subtract(netRatio).divide(new BigDecimal("0.50"), 4, RoundingMode.HALF_UP));
            }
            BigDecimal contrib = norm.multiply(wDeficit).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            deficitFactor = new RiskDtos.RiskFactorDto(
                    "CASH_FLOW_DEFICIT", "Cash-Flow Deficit Pressure", wDeficit, norm, contrib,
                    netRatio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%",
                    "% net cash-flow margin", "Trailing 30d & 30d outlook",
                    "clamp((0.20 - netMargin) / 0.50, 0, 1)",
                    true,
                    "Estimated net cash-flow margin is " + netRatio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%."
            );
        }
        factors.add(deficitFactor);

        // 3. Emergency savings coverage (20%)
        BigDecimal wEmergency = new BigDecimal("0.2000");
        RiskDtos.RiskFactorDto emergencyFactor;
        BigDecimal essentialBase = hasEssential ? input.monthlyEssentialExpenses() : input.monthlyTotalExpenses();
        if (!input.hasAccounts() || essentialBase == null || essentialBase.compareTo(ZERO) <= 0) {
            assumptions.add("Emergency savings coverage requires an active liquid account and positive essential expenses.");
            emergencyFactor = new RiskDtos.RiskFactorDto(
                    "EMERGENCY_SAVINGS_COVERAGE", "Emergency Savings Coverage", wEmergency, null, null,
                    "Unavailable", "months of essential cover", "Current liquid snapshot",
                    "1 - clamp((liquidBalance / monthlyEssentialOutflow) / 6.0, 0, 1)",
                    false, "Requires liquid balance and essential monthly outflow baseline."
            );
        } else {
            BigDecimal bal = input.availableLiquidBalance() != null ? input.availableLiquidBalance() : ZERO;
            BigDecimal months = bal.max(ZERO).divide(essentialBase, 4, RoundingMode.HALF_UP);
            BigDecimal norm = ONE.subtract(clamp01(months.divide(new BigDecimal("6.0000"), 4, RoundingMode.HALF_UP)));
            BigDecimal contrib = norm.multiply(wEmergency).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            emergencyFactor = new RiskDtos.RiskFactorDto(
                    "EMERGENCY_SAVINGS_COVERAGE", "Emergency Savings Coverage", wEmergency, norm, contrib,
                    months.setScale(1, RoundingMode.HALF_UP) + " mo",
                    "months of essential cover", "Current liquid snapshot",
                    "1 - clamp(coverageMonths / 6.0, 0, 1)",
                    true,
                    "Liquid funds cover approximately " + months.setScale(1, RoundingMode.HALF_UP) + " months of essential outflows (6 months = target baseline)."
            );
        }
        factors.add(emergencyFactor);

        // 4. Expense pressure (15%)
        BigDecimal wExpense = new BigDecimal("0.1500");
        RiskDtos.RiskFactorDto expenseFactor;
        if (!hasIncome || !hasExpenses) {
            assumptions.add("Expense pressure requires both monthly income and expense records.");
            expenseFactor = new RiskDtos.RiskFactorDto(
                    "EXPENSE_PRESSURE", "Expense Pressure", wExpense, null, null,
                    "Unavailable", "% of monthly income", "Trailing 30 days",
                    "clamp((expenseRatio - 0.35) / 0.55, 0, 1)",
                    false, "Add expense entries and income to measure expenditure pressure."
            );
        } else {
            BigDecimal expRatio = input.monthlyTotalExpenses().divide(input.monthlyIncome(), 4, RoundingMode.HALF_UP);
            BigDecimal norm = clamp01(expRatio.subtract(new BigDecimal("0.35")).divide(new BigDecimal("0.55"), 4, RoundingMode.HALF_UP));
            BigDecimal contrib = norm.multiply(wExpense).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            expenseFactor = new RiskDtos.RiskFactorDto(
                    "EXPENSE_PRESSURE", "Expense Pressure", wExpense, norm, contrib,
                    expRatio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%",
                    "% of monthly income", "Trailing 30 days",
                    "clamp((expenseRatio - 0.35) / 0.55, 0, 1)",
                    true,
                    "Monthly expenses consume " + expRatio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "% of monthly income."
            );
        }
        factors.add(expenseFactor);

        // 5. Income variability (10%)
        BigDecimal wVariability = new BigDecimal("0.1000");
        RiskDtos.RiskFactorDto varFactor;
        if (!hasIncome || input.incomeStabilityAvg() == null) {
            assumptions.add("Income variability requires active income sources or multi-month history.");
            varFactor = new RiskDtos.RiskFactorDto(
                    "INCOME_VARIABILITY", "Income Variability", wVariability, null, null,
                    "Unavailable", "variability index (0-1)", "Trailing 90 days",
                    "1 - clamp(incomeStabilityScore, 0, 1)",
                    false, "Insufficient multi-period income history to measure income volatility."
            );
        } else {
            BigDecimal norm = clamp01(ONE.subtract(input.incomeStabilityAvg()));
            if (input.historicalMonthsCount() < 2) {
                assumptions.add("Income variability inferred from declared income stability score (< 2 historical months available).");
            }
            BigDecimal contrib = norm.multiply(wVariability).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            varFactor = new RiskDtos.RiskFactorDto(
                    "INCOME_VARIABILITY", "Income Variability", wVariability, norm, contrib,
                    norm.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%",
                    "variability index", "Trailing 90 days",
                    "1 - clamp(incomeStabilityScore, 0, 1)",
                    true,
                    "Income variability index is " + norm.setScale(2, RoundingMode.HALF_UP) + " based on income stability and history."
            );
        }
        factors.add(varFactor);

        // 6. Payment urgency (5%)
        BigDecimal wUrgency = new BigDecimal("0.0500");
        RiskDtos.RiskFactorDto urgencyFactor;
        if (!input.hasAccounts() || !input.hasObligationsData()) {
            assumptions.add("Payment urgency unavailable without account balances and upcoming obligations.");
            urgencyFactor = new RiskDtos.RiskFactorDto(
                    "PAYMENT_URGENCY", "Near-Term Payment Urgency", wUrgency, null, null,
                    "Unavailable", "14-day due / liquid", "Next 14 days",
                    "clamp((due14d / max(liquidBalance, 1)) + overdueCount * 0.20, 0, 1)",
                    false, "Add obligations and liquid account balance to evaluate 14-day payment pressure."
            );
        } else {
            BigDecimal denom = input.availableLiquidBalance() != null && input.availableLiquidBalance().compareTo(ONE) > 0
                    ? input.availableLiquidBalance() : ONE;
            BigDecimal dueRatio = input.dueWithin14Days().divide(denom, 4, RoundingMode.HALF_UP);
            BigDecimal overduePenalty = new BigDecimal(input.overdueCount()).multiply(new BigDecimal("0.20"));
            BigDecimal norm = clamp01(dueRatio.add(overduePenalty));
            BigDecimal contrib = norm.multiply(wUrgency).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            urgencyFactor = new RiskDtos.RiskFactorDto(
                    "PAYMENT_URGENCY", "Near-Term Payment Urgency", wUrgency, norm, contrib,
                    input.dueWithin14Days().setScale(0, RoundingMode.HALF_UP) + " (" + input.overdueCount() + " overdue)",
                    "14-day due vs liquid", "Next 14 days",
                    "clamp((due14d / max(liquidBalance, 1)) + overdueCount * 0.20, 0, 1)",
                    true,
                    input.dueWithin14Days().setScale(2, RoundingMode.HALF_UP) + " due within 14 days; " + input.overdueCount() + " overdue obligations."
            );
        }
        factors.add(urgencyFactor);

        // Aggregate available factors
        BigDecimal availableWeight = ZERO;
        BigDecimal weightedSum = ZERO;
        for (RiskDtos.RiskFactorDto f : factors) {
            if (f.dataAvailable() && f.normalizedValue() != null) {
                availableWeight = availableWeight.add(f.weight());
                weightedSum = weightedSum.add(f.weight().multiply(f.normalizedValue()));
            }
        }

        BigDecimal dataCompleteness = availableWeight.setScale(4, RoundingMode.HALF_UP);
        boolean insufficientData = availableWeight.compareTo(new BigDecimal("0.3500")) < 0;

        BigDecimal rawScore;
        if (availableWeight.compareTo(ZERO) == 0) {
            rawScore = ZERO;
        } else {
            // Re-normalize across observed factor weights so unavailable factors are never silently zeroed
            rawScore = weightedSum.divide(availableWeight, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        }

        int displayScore = rawScore.setScale(0, RoundingMode.HALF_UP).intValue();
        String category = insufficientData ? "INSUFFICIENT_DATA" : classify(displayScore);

        return new EvaluationResult(
                POLICY_VERSION,
                rawScore,
                displayScore,
                category,
                dataCompleteness,
                insufficientData,
                assumptions,
                factors
        );
    }

    public static String classify(int score) {
        if (score <= 30) return "LOW";
        if (score <= 60) return "MODERATE";
        if (score <= 80) return "HIGH";
        return "CRITICAL";
    }

    private static BigDecimal clamp01(BigDecimal value) {
        if (value.compareTo(ZERO) < 0) return ZERO;
        if (value.compareTo(ONE) > 0) return ONE;
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
