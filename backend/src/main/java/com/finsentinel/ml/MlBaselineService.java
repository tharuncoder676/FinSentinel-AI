package com.finsentinel.ml;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.ledger.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MlBaselineService {

    public static final String MODEL_VERSION = "stat-baseline-v1.0";

    private static final Map<String, String> KEYWORD_TO_CATEGORY_NAME = Map.ofEntries(
            Map.entry("rent", "Housing & Rent"),
            Map.entry("landlord", "Housing & Rent"),
            Map.entry("electricity", "Utilities & Telecom"),
            Map.entry("bescom", "Utilities & Telecom"),
            Map.entry("airtel", "Utilities & Telecom"),
            Map.entry("jio", "Utilities & Telecom"),
            Map.entry("broadband", "Utilities & Telecom"),
            Map.entry("bigbasket", "Groceries & Essentials"),
            Map.entry("zepto", "Groceries & Essentials"),
            Map.entry("grocery", "Groceries & Essentials"),
            Map.entry("dmart", "Groceries & Essentials"),
            Map.entry("pharmacy", "Healthcare & Insurance"),
            Map.entry("apollo", "Healthcare & Insurance"),
            Map.entry("hospital", "Healthcare & Insurance"),
            Map.entry("insurance", "Healthcare & Insurance"),
            Map.entry("emi", "Loan & EMI Payment"),
            Map.entry("loan", "Loan & EMI Payment"),
            Map.entry("hdfc", "Loan & EMI Payment"),
            Map.entry("fuel", "Transport & Fuel"),
            Map.entry("uber", "Transport & Fuel"),
            Map.entry("metro", "Transport & Fuel"),
            Map.entry("swiggy", "Dining & Discretionary"),
            Map.entry("zomato", "Dining & Discretionary"),
            Map.entry("netflix", "Subscriptions & Digital"),
            Map.entry("spotify", "Subscriptions & Digital"),
            Map.entry("salary", "Salary & Wages"),
            Map.entry("payroll", "Salary & Wages")
    );

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;

    public MlBaselineService(TransactionRepository transactionRepository,
                             TransactionCategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    public record AnomalyFinding(
            UUID transactionId,
            LocalDate occurredOn,
            String merchant,
            BigDecimal amount,
            BigDecimal baselineMean,
            double zScore,
            String status,
            String explanation
    ) {}

    public record CategorySuggestion(
            UUID transactionId,
            String merchant,
            UUID suggestedCategoryId,
            String suggestedCategoryName,
            String basis
    ) {}

    public record RecurringPattern(
            String merchant,
            BigDecimal typicalAmount,
            int occurrences,
            String estimatedCadence
    ) {}

    public record EvaluationSummary(
            String evaluationStatus,
            int sampleSize,
            BigDecimal exponentialSmoothingMae,
            String nextStep
    ) {}

    public record MlInsightsResponse(
            String modelVersion,
            String dataSufficiency,
            BigDecimal movingAverageMonthlyExpense,
            BigDecimal exponentialSmoothingNextMonthExpense,
            List<String> knownLimitations,
            EvaluationSummary evaluation,
            List<AnomalyFinding> anomalies,
            List<CategorySuggestion> categorySuggestions,
            List<RecurringPattern> recurringPatterns
    ) {}

    @Transactional
    public MlInsightsResponse analyzeUser(UUID userId) {
        List<TransactionEntity> txs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        List<TransactionEntity> expenses = txs.stream()
                .filter(t -> "EXPENSE".equals(t.getTxType()))
                .sorted(Comparator.comparing(TransactionEntity::getOccurredOn))
                .toList();

        List<String> limitations = List.of(
                "Uses deterministic statistical baselines (moving average, alpha=0.35 exponential smoothing, z-score >= 2.0, keyword rules) rather than a trained deep-learning model.",
                "Unusual spend flags indicate statistical deviation from user history, never fraud.",
                "Category learning is strictly scoped to the authenticated user's corrections."
        );

        if (expenses.size() < 4) {
            return new MlInsightsResponse(
                    MODEL_VERSION,
                    "INSUFFICIENT_DATA",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    limitations,
                    new EvaluationSummary("NOT_VALIDATED", expenses.size(), null, "Record at least 12 historical expenses across multiple months to evaluate baseline forecast MAE."),
                    List.of(),
                    suggestCategories(userId, txs),
                    List.of()
            );
        }

        // Mean & StdDev for anomaly detection
        double[] vals = expenses.stream().mapToDouble(t -> t.getAmount().doubleValue()).toArray();
        double mean = Arrays.stream(vals).average().orElse(0.0);
        double variance = Arrays.stream(vals).map(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        List<AnomalyFinding> anomalies = new ArrayList<>();
        if (stdDev > 0.01) {
            for (TransactionEntity tx : expenses) {
                double z = (tx.getAmount().doubleValue() - mean) / stdDev;
                if (z >= 1.95 && !"DISMISSED".equals(tx.getAnomalyStatus())) {
                    if ("NONE".equals(tx.getAnomalyStatus())) {
                        tx.setAnomalyStatus("FLAGGED");
                        transactionRepository.save(tx);
                    }
                    anomalies.add(new AnomalyFinding(
                            tx.getId(), tx.getOccurredOn(),
                            tx.getMerchant() != null ? tx.getMerchant() : "Expense",
                            tx.getAmount().setScale(2, RoundingMode.HALF_UP),
                            BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP),
                            Math.round(z * 100.0) / 100.0,
                            tx.getAnomalyStatus(),
                            "Amount is " + (Math.round(z * 10.0) / 10.0) + " standard deviations above your historical expense mean."
                    ));
                }
            }
        }

        // Monthly aggregation for moving average + exponential smoothing
        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
        for (TransactionEntity tx : expenses) {
            String ym = tx.getOccurredOn().getYear() + "-" + String.format("%02d", tx.getOccurredOn().getMonthValue());
            monthlyTotals.merge(ym, tx.getAmount(), BigDecimal::add);
        }

        List<BigDecimal> series = new ArrayList<>(monthlyTotals.values());
        BigDecimal movingAvg = series.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(series.size()), 2, RoundingMode.HALF_UP);

        // Exponential smoothing (alpha = 0.35) with time-ordered one-step-ahead MAE
        double alpha = 0.35;
        double smoothed = series.get(0).doubleValue();
        double absErrSum = 0.0;
        int evalPoints = 0;
        for (int i = 1; i < series.size(); i++) {
            double actual = series.get(i).doubleValue();
            absErrSum += Math.abs(actual - smoothed);
            evalPoints++;
            smoothed = alpha * actual + (1.0 - alpha) * smoothed;
        }

        EvaluationSummary eval = evalPoints >= 2
                ? new EvaluationSummary("SYNTHETIC_OR_USER_BASELINE_MEASURED", series.size(),
                BigDecimal.valueOf(absErrSum / evalPoints).setScale(2, RoundingMode.HALF_UP),
                "Time-ordered one-step-ahead MAE measured across " + evalPoints + " monthly transitions; train Smile/Tribuo classifier only once >= 200 labeled transactions accumulate.")
                : new EvaluationSummary("NOT_VALIDATED", series.size(), null,
                "Need >= 3 distinct months to compute out-of-sample one-step-ahead MAE.");

        return new MlInsightsResponse(
                MODEL_VERSION,
                series.size() >= 3 ? "SUFFICIENT" : "PARTIAL",
                movingAvg,
                BigDecimal.valueOf(smoothed).setScale(2, RoundingMode.HALF_UP),
                limitations,
                eval,
                anomalies,
                suggestCategories(userId, txs),
                detectRecurring(expenses)
        );
    }

    @Transactional
    public void updateAnomalyStatus(UserSessionPrincipal principal, UUID txId, String status) {
        TransactionEntity tx = transactionRepository.findByIdAndUserId(txId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        tx.setAnomalyStatus(status);
        transactionRepository.save(tx);
    }

    @Transactional
    public void applyCategoryCorrection(UserSessionPrincipal principal, UUID txId, UUID categoryId) {
        TransactionEntity tx = transactionRepository.findByIdAndUserId(txId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        TransactionCategoryEntity cat = categoryRepository.findById(categoryId)
                .filter(c -> c.isSystem() || principal.userId().equals(c.getUserId()))
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Category not found"));
        tx.setCategoryId(cat.getId());
        tx.setEssential(cat.isEssential());
        tx.setSuggestedCategoryId(null);
        transactionRepository.save(tx);
    }

    private List<CategorySuggestion> suggestCategories(UUID userId, List<TransactionEntity> txs) {
        Map<String, TransactionCategoryEntity> byName = categoryRepository.findAvailableForUser(userId).stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(), c -> c, (a, b) -> a));

        // User-scoped merchant memory from existing categorized transactions
        Map<String, UUID> userLearnedMerchantMap = new HashMap<>();
        for (TransactionEntity tx : txs) {
            if (tx.getCategoryId() != null && tx.getMerchant() != null && !tx.getMerchant().isBlank()) {
                userLearnedMerchantMap.putIfAbsent(tx.getMerchant().trim().toLowerCase(), tx.getCategoryId());
            }
        }

        List<CategorySuggestion> suggestions = new ArrayList<>();
        for (TransactionEntity tx : txs) {
            if (tx.getCategoryId() != null || "TRANSFER".equals(tx.getTxType())) continue;
            String text = ((tx.getMerchant() != null ? tx.getMerchant() : "") + " " + (tx.getDescription() != null ? tx.getDescription() : "")).toLowerCase();
            if (tx.getMerchant() != null && userLearnedMerchantMap.containsKey(tx.getMerchant().trim().toLowerCase())) {
                UUID catId = userLearnedMerchantMap.get(tx.getMerchant().trim().toLowerCase());
                String catName = byName.values().stream().filter(c -> c.getId().equals(catId)).map(TransactionCategoryEntity::getName).findFirst().orElse("Matched Category");
                suggestions.add(new CategorySuggestion(tx.getId(), tx.getMerchant(), catId, catName, "Matched from your previous categorized entries for this merchant"));
                continue;
            }
            for (Map.Entry<String, String> entry : KEYWORD_TO_CATEGORY_NAME.entrySet()) {
                if (text.contains(entry.getKey())) {
                    TransactionCategoryEntity cat = byName.get(entry.getValue().toLowerCase());
                    if (cat != null) {
                        suggestions.add(new CategorySuggestion(tx.getId(), tx.getMerchant(), cat.getId(), cat.getName(), "Keyword rule '" + entry.getKey() + "'"));
                        break;
                    }
                }
            }
        }
        return suggestions;
    }

    private List<RecurringPattern> detectRecurring(List<TransactionEntity> expenses) {
        Map<String, List<TransactionEntity>> byMerchant = expenses.stream()
                .filter(t -> t.getMerchant() != null && !t.getMerchant().isBlank())
                .collect(Collectors.groupingBy(t -> t.getMerchant().trim().toLowerCase()));

        List<RecurringPattern> out = new ArrayList<>();
        for (Map.Entry<String, List<TransactionEntity>> e : byMerchant.entrySet()) {
            if (e.getValue().size() >= 2) {
                BigDecimal avg = e.getValue().stream().map(TransactionEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP);
                out.add(new RecurringPattern(e.getValue().get(0).getMerchant(), avg, e.getValue().size(), "MONTHLY_ESTIMATED"));
            }
        }
        return out;
    }
}
