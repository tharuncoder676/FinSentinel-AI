package com.finsentinel.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.security.UserSessionPrincipal;
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
public class RiskEngineService {

    public static final String EDUCATIONAL_DISCLAIMER =
            "Educational vulnerability estimate only. Not a credit bureau score, loan eligibility judgment, or personalized investment advice.";

    private final RiskPolicyFactory policyFactory;
    private final RiskAssessmentRepository assessmentRepository;
    private final RiskFactorScoreRepository factorScoreRepository;
    private final AccountRepository accountRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final FinancialObligationRepository obligationRepository;
    private final FinancialProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public RiskEngineService(RiskPolicyFactory policyFactory,
                             RiskAssessmentRepository assessmentRepository,
                             RiskFactorScoreRepository factorScoreRepository,
                             AccountRepository accountRepository,
                             IncomeSourceRepository incomeSourceRepository,
                             TransactionRepository transactionRepository,
                             ExpenseRepository expenseRepository,
                             FinancialObligationRepository obligationRepository,
                             FinancialProfileRepository profileRepository,
                             ObjectMapper objectMapper) {
        this.policyFactory = policyFactory;
        this.assessmentRepository = assessmentRepository;
        this.factorScoreRepository = factorScoreRepository;
        this.accountRepository = accountRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.transactionRepository = transactionRepository;
        this.expenseRepository = expenseRepository;
        this.obligationRepository = obligationRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RiskDtos.RiskAssessmentDto assessAndPersist(UUID userId, LocalDate asOfDate) {
        RiskDtos.RiskInputSnapshot snapshot = buildInputSnapshot(userId, asOfDate);
        RiskPolicyStrategy.EvaluationResult result = policyFactory.getActivePolicy().evaluate(snapshot);

        Instant now = Instant.now();
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setPolicyVersion(result.policyVersion());
        entity.setRawScore(result.rawScore());
        entity.setDisplayScore(result.displayScore());
        entity.setCategory(result.category());
        entity.setDataCompleteness(result.dataCompleteness());
        entity.setInsufficientData(result.insufficientData());
        entity.setAssumptionsJson(toJson(result.assumptions()));
        entity.setCalculatedAt(now);
        assessmentRepository.save(entity);

        for (RiskDtos.RiskFactorDto f : result.factors()) {
            RiskFactorScoreEntity fs = new RiskFactorScoreEntity();
            fs.setId(UUID.randomUUID());
            fs.setAssessmentId(entity.getId());
            fs.setFactorKey(f.factorKey());
            fs.setFactorLabel(f.factorLabel());
            fs.setWeight(f.weight());
            fs.setNormalizedValue(f.normalizedValue());
            fs.setWeightedContribution(f.weightedContribution());
            fs.setMeasuredValue(f.measuredValue());
            fs.setUnit(f.unit());
            fs.setDataAvailable(f.dataAvailable());
            fs.setExplanation(f.explanation());
            factorScoreRepository.save(fs);
        }

        return new RiskDtos.RiskAssessmentDto(
                entity.getId(),
                result.policyVersion(),
                result.rawScore(),
                result.displayScore(),
                result.category(),
                result.dataCompleteness(),
                result.insufficientData(),
                EDUCATIONAL_DISCLAIMER,
                result.assumptions(),
                result.factors(),
                now
        );
    }

    @Transactional
    public RiskDtos.RiskAssessmentDto getLatestOrCompute(UUID userId) {
        return assessAndPersist(userId, LocalDate.now());
    }

    public RiskDtos.RiskAssessmentDto evaluateSyntheticSnapshot(RiskDtos.RiskInputSnapshot snapshot) {
        RiskPolicyStrategy.EvaluationResult result = policyFactory.getActivePolicy().evaluate(snapshot);
        return new RiskDtos.RiskAssessmentDto(
                null,
                result.policyVersion(),
                result.rawScore(),
                result.displayScore(),
                result.category(),
                result.dataCompleteness(),
                result.insufficientData(),
                EDUCATIONAL_DISCLAIMER,
                result.assumptions(),
                result.factors(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<RiskDtos.RiskHistoryPoint> getHistory(UUID userId) {
        return assessmentRepository.findTop24ByUserIdOrderByCalculatedAtDesc(userId).stream()
                .map(a -> new RiskDtos.RiskHistoryPoint(
                        a.getId(), a.getPolicyVersion(), a.getDisplayScore(), a.getRawScore(),
                        a.getCategory(), a.getDataCompleteness(), a.getCalculatedAt()))
                .toList();
    }

    public RiskDtos.RiskInputSnapshot buildInputSnapshot(UUID userId, LocalDate asOfDate) {
        List<AccountEntity> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<TransactionEntity> allTxs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        List<IncomeSourceEntity> incomes = incomeSourceRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<ExpenseEntity> expenses = expenseRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<FinancialObligationEntity> obligations = obligationRepository.findByUserIdOrderByDueDateAsc(userId);
        FinancialProfileEntity profile = profileRepository.findById(userId).orElse(null);

        BigDecimal liquidBalance = BigDecimal.ZERO;
        for (AccountEntity acc : accounts) {
            if (!"CREDIT_CARD".equals(acc.getAccountType())) {
                BigDecimal bal = acc.getOpeningBalance();
                for (TransactionEntity tx : allTxs) {
                    if (tx.getOccurredOn().isBefore(acc.getOpeningBalanceDate())) continue;
                    if (acc.getId().equals(tx.getAccountId())) {
                        switch (tx.getTxType()) {
                            case "INCOME", "REFUND" -> bal = bal.add(tx.getAmount());
                            case "EXPENSE", "SAVINGS", "TRANSFER" -> bal = bal.subtract(tx.getAmount());
                            default -> {}
                        }
                    } else if (acc.getId().equals(tx.getTransferAccountId()) && "TRANSFER".equals(tx.getTxType())) {
                        bal = bal.add(tx.getAmount());
                    }
                }
                liquidBalance = liquidBalance.add(bal);
            }
        }
        if (accounts.isEmpty() && profile != null && profile.getLiquidFunds() != null) {
            liquidBalance = profile.getLiquidFunds();
        }

        // Monthly income: active income sources normalized to monthly, else trailing 30d tx, else profile
        BigDecimal monthlyIncome = incomes.stream()
                .filter(IncomeSourceEntity::isActive)
                .map(i -> normalizeMonthly(i.getAmount(), i.getFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate thirtyAgo = asOfDate.minusDays(30);
            monthlyIncome = allTxs.stream()
                    .filter(tx -> "INCOME".equals(tx.getTxType()) && !tx.getOccurredOn().isBefore(thirtyAgo) && !tx.getOccurredOn().isAfter(asOfDate))
                    .map(TransactionEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0 && profile != null && profile.getMonthlyIncome() != null) {
            monthlyIncome = profile.getMonthlyIncome().add(profile.getOtherIncome() != null ? profile.getOtherIncome() : BigDecimal.ZERO);
        }

        // Monthly expenses: trailing 30d expenses minus refunds, or active expense templates, or profile
        LocalDate thirtyAgo = asOfDate.minusDays(30);
        BigDecimal txExpenses = BigDecimal.ZERO;
        BigDecimal txEssential = BigDecimal.ZERO;
        Set<YearMonth> activeMonths = new HashSet<>();
        for (TransactionEntity tx : allTxs) {
            activeMonths.add(YearMonth.from(tx.getOccurredOn()));
            if (!tx.getOccurredOn().isBefore(thirtyAgo) && !tx.getOccurredOn().isAfter(asOfDate)) {
                if ("EXPENSE".equals(tx.getTxType())) {
                    txExpenses = txExpenses.add(tx.getAmount());
                    if (tx.isEssential()) txEssential = txEssential.add(tx.getAmount());
                } else if ("REFUND".equals(tx.getTxType())) {
                    txExpenses = txExpenses.subtract(tx.getAmount());
                }
            }
        }
        if (txExpenses.compareTo(BigDecimal.ZERO) < 0) txExpenses = BigDecimal.ZERO;

        BigDecimal templateExpenses = expenses.stream().filter(ExpenseEntity::isActive)
                .map(e -> normalizeMonthly(e.getAmount(), e.getFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal templateEssential = expenses.stream().filter(e -> e.isActive() && e.isEssential())
                .map(e -> normalizeMonthly(e.getAmount(), e.getFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyTotalExpenses = txExpenses.compareTo(BigDecimal.ZERO) > 0 ? txExpenses : templateExpenses;
        if (monthlyTotalExpenses.compareTo(BigDecimal.ZERO) == 0 && profile != null && profile.getRegularExpenses() != null) {
            monthlyTotalExpenses = profile.getRegularExpenses();
        }
        BigDecimal monthlyEssentialExpenses = txEssential.compareTo(BigDecimal.ZERO) > 0 ? txEssential : templateEssential;
        if (monthlyEssentialExpenses.compareTo(BigDecimal.ZERO) == 0 && monthlyTotalExpenses.compareTo(BigDecimal.ZERO) > 0) {
            monthlyEssentialExpenses = monthlyTotalExpenses.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP);
        }

        // Obligations due in next 30d and next 14d
        LocalDate in30 = asOfDate.plusDays(30);
        LocalDate in14 = asOfDate.plusDays(14);
        BigDecimal obligations30 = BigDecimal.ZERO;
        BigDecimal due14 = BigDecimal.ZERO;
        int overdueCount = 0;
        for (FinancialObligationEntity ob : obligations) {
            BigDecimal rem = ob.getRemainingAmount();
            if (rem.compareTo(BigDecimal.ZERO) <= 0 || "PAID".equals(ob.getStatus())) continue;
            if (ob.getDueDate().isBefore(asOfDate)) {
                overdueCount++;
                obligations30 = obligations30.add(rem);
                due14 = due14.add(rem);
            } else {
                if (!ob.getDueDate().isAfter(in30)) obligations30 = obligations30.add(rem);
                if (!ob.getDueDate().isAfter(in14)) due14 = due14.add(rem);
            }
        }

        BigDecimal stabilityAvg = null;
        if (!incomes.isEmpty()) {
            BigDecimal sum = incomes.stream()
                    .filter(IncomeSourceEntity::isActive)
                    .map(i -> i.getStabilityScore() != null ? i.getStabilityScore() : new BigDecimal("0.85"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = incomes.stream().filter(IncomeSourceEntity::isActive).count();
            if (count > 0) {
                stabilityAvg = sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
            }
        }

        boolean hasAccounts = !accounts.isEmpty() || (profile != null && profile.getLiquidFunds() != null);
        boolean hasObligationsData = !obligations.isEmpty() || monthlyIncome.compareTo(BigDecimal.ZERO) > 0;

        return new RiskDtos.RiskInputSnapshot(
                asOfDate,
                liquidBalance.setScale(2, RoundingMode.HALF_UP),
                monthlyIncome.setScale(2, RoundingMode.HALF_UP),
                monthlyTotalExpenses.setScale(2, RoundingMode.HALF_UP),
                monthlyEssentialExpenses.setScale(2, RoundingMode.HALF_UP),
                obligations30.setScale(2, RoundingMode.HALF_UP),
                due14.setScale(2, RoundingMode.HALF_UP),
                overdueCount,
                stabilityAvg,
                activeMonths.size(),
                hasAccounts,
                hasObligationsData
        );
    }

    public static BigDecimal normalizeMonthly(BigDecimal amount, String frequency) {
        if (amount == null) return BigDecimal.ZERO;
        return switch (frequency != null ? frequency : "MONTHLY") {
            case "WEEKLY" -> amount.multiply(new BigDecimal("4.3333")).setScale(2, RoundingMode.HALF_UP);
            case "BIWEEKLY" -> amount.multiply(new BigDecimal("2.1667")).setScale(2, RoundingMode.HALF_UP);
            case "QUARTERLY" -> amount.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            case "ANNUAL" -> amount.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            case "ONE_TIME" -> BigDecimal.ZERO;
            default -> amount.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
