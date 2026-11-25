package com.finsentinel.ledger;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.api.PagedResponse;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.TokenUtil;
import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final AuditService auditService;

    public LedgerService(AccountRepository accountRepository,
                         TransactionCategoryRepository categoryRepository,
                         IncomeSourceRepository incomeSourceRepository,
                         TransactionRepository transactionRepository,
                         ExpenseRepository expenseRepository,
                         SavingsGoalRepository savingsGoalRepository,
                         AuditService auditService) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.transactionRepository = transactionRepository;
        this.expenseRepository = expenseRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.AccountDto> listAccounts(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<TransactionEntity> allTxs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        return accounts.stream()
                .map(acc -> toAccountDto(acc, computeAccountBalance(acc, allTxs)))
                .toList();
    }

    @Transactional
    public LedgerDtos.AccountDto createAccount(UserSessionPrincipal principal, LedgerDtos.AccountRequest req) {
        Instant now = Instant.now();
        AccountEntity acc = new AccountEntity();
        acc.setId(UUID.randomUUID());
        acc.setUserId(principal.userId());
        acc.setName(req.name().trim());
        acc.setAccountType(req.accountType());
        acc.setCurrency(req.currency() != null ? req.currency() : "INR");
        acc.setOpeningBalance(req.openingBalance().setScale(4, RoundingMode.HALF_UP));
        acc.setOpeningBalanceDate(req.openingBalanceDate());
        acc.setCreatedAt(now);
        acc.setUpdatedAt(now);
        accountRepository.save(acc);
        auditService.record(principal.userId(), principal.role(), "ACCOUNT_CREATED", "ACCOUNT", acc.getId().toString(), "SUCCESS", Map.of());
        return toAccountDto(acc, acc.getOpeningBalance());
    }

    @Transactional
    public LedgerDtos.AccountDto updateAccount(UserSessionPrincipal principal, UUID accountId, LedgerDtos.AccountRequest req) {
        AccountEntity acc = accountRepository.findByIdAndUserId(accountId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        acc.setName(req.name().trim());
        acc.setAccountType(req.accountType());
        if (req.currency() != null) acc.setCurrency(req.currency());
        acc.setOpeningBalance(req.openingBalance().setScale(4, RoundingMode.HALF_UP));
        acc.setOpeningBalanceDate(req.openingBalanceDate());
        acc.setUpdatedAt(Instant.now());
        accountRepository.save(acc);
        List<TransactionEntity> allTxs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(principal.userId());
        return toAccountDto(acc, computeAccountBalance(acc, allTxs));
    }

    @Transactional
    public void deleteAccount(UserSessionPrincipal principal, UUID accountId) {
        AccountEntity acc = accountRepository.findByIdAndUserId(accountId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        acc.setArchived(true);
        acc.setUpdatedAt(Instant.now());
        accountRepository.save(acc);
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.CategoryDto> listCategories(UUID userId) {
        return categoryRepository.findAvailableForUser(userId).stream()
                .map(c -> new LedgerDtos.CategoryDto(c.getId(), c.getName(), c.getKind(), c.isEssential(), c.getColorToken(), c.isSystem()))
                .toList();
    }

    @Transactional
    public LedgerDtos.CategoryDto createCategory(UserSessionPrincipal principal, LedgerDtos.CategoryRequest req) {
        TransactionCategoryEntity c = new TransactionCategoryEntity();
        c.setId(UUID.randomUUID());
        c.setUserId(principal.userId());
        c.setName(req.name().trim());
        c.setKind(req.kind());
        c.setEssential(req.essential());
        c.setColorToken(req.colorToken() != null ? req.colorToken() : "slate");
        c.setSystem(false);
        c.setCreatedAt(Instant.now());
        categoryRepository.save(c);
        return new LedgerDtos.CategoryDto(c.getId(), c.getName(), c.getKind(), c.isEssential(), c.getColorToken(), c.isSystem());
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.IncomeSourceDto> listIncomeSources(UUID userId) {
        return incomeSourceRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toIncomeDto)
                .toList();
    }

    @Transactional
    public LedgerDtos.IncomeSourceDto createIncomeSource(UserSessionPrincipal principal, LedgerDtos.IncomeSourceRequest req) {
        if (req.accountId() != null) {
            accountRepository.findByIdAndUserId(req.accountId(), principal.userId())
                    .orElseThrow(() -> ApiException.notFound("Target account not found"));
        }
        Instant now = Instant.now();
        IncomeSourceEntity inc = new IncomeSourceEntity();
        inc.setId(UUID.randomUUID());
        inc.setUserId(principal.userId());
        inc.setAccountId(req.accountId());
        inc.setName(req.name().trim());
        inc.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        inc.setFrequency(req.frequency());
        inc.setExpectedDay(req.expectedDay());
        inc.setNextExpectedDate(req.nextExpectedDate());
        inc.setStabilityScore(req.stabilityScore() != null ? req.stabilityScore() : new BigDecimal("0.9000"));
        inc.setActive(req.active() == null || req.active());
        inc.setCreatedAt(now);
        inc.setUpdatedAt(now);
        incomeSourceRepository.save(inc);
        return toIncomeDto(inc);
    }

    @Transactional
    public LedgerDtos.IncomeSourceDto updateIncomeSource(UserSessionPrincipal principal, UUID id, LedgerDtos.IncomeSourceRequest req) {
        IncomeSourceEntity inc = incomeSourceRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Income source not found"));
        inc.setAccountId(req.accountId());
        inc.setName(req.name().trim());
        inc.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        inc.setFrequency(req.frequency());
        inc.setExpectedDay(req.expectedDay());
        inc.setNextExpectedDate(req.nextExpectedDate());
        if (req.stabilityScore() != null) inc.setStabilityScore(req.stabilityScore());
        if (req.active() != null) inc.setActive(req.active());
        inc.setUpdatedAt(Instant.now());
        incomeSourceRepository.save(inc);
        return toIncomeDto(inc);
    }

    @Transactional
    public void deleteIncomeSource(UserSessionPrincipal principal, UUID id) {
        IncomeSourceEntity inc = incomeSourceRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Income source not found"));
        incomeSourceRepository.delete(inc);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LedgerDtos.TransactionDto> listTransactions(
            UUID userId, String search, String txType, UUID categoryId, UUID accountId,
            LocalDate from, LocalDate to, int page, int size, String sortBy, String sortDir) {

        String safeSort = switch (sortBy != null ? sortBy : "occurredOn") {
            case "amount" -> "amount";
            case "merchant" -> "merchant";
            case "txType" -> "txType";
            default -> "occurredOn";
        };
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, safeSort)
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<TransactionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (txType != null && !txType.isBlank()) {
                predicates.add(cb.equal(root.get("txType"), txType));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (accountId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("accountId"), accountId),
                        cb.equal(root.get("transferAccountId"), accountId)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), to));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("merchant"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TransactionEntity> txPage = transactionRepository.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort));
        Map<UUID, String> accountNames = accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getName));
        Map<UUID, String> categoryNames = categoryRepository.findAvailableForUser(userId).stream()
                .collect(Collectors.toMap(TransactionCategoryEntity::getId, TransactionCategoryEntity::getName));

        List<LedgerDtos.TransactionDto> items = txPage.getContent().stream()
                .map(tx -> toTransactionDto(tx, accountNames, categoryNames, false))
                .toList();

        return new PagedResponse<>(items, txPage.getNumber(), txPage.getSize(), txPage.getTotalElements(), txPage.getTotalPages());
    }

    @Transactional
    public LedgerDtos.TransactionDto createTransaction(UserSessionPrincipal principal, LedgerDtos.TransactionRequest req) {
        AccountEntity sourceAccount = accountRepository.findByIdAndUserId(req.accountId(), principal.userId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));

        if ("TRANSFER".equals(req.txType())) {
            if (req.transferAccountId() == null || req.transferAccountId().equals(req.accountId())) {
                throw ApiException.badRequest("INVALID_TRANSFER", "Transfers require a distinct destination account");
            }
            accountRepository.findByIdAndUserId(req.transferAccountId(), principal.userId())
                    .orElseThrow(() -> ApiException.notFound("Destination transfer account not found"));
        }

        String dedupHash = computeDedupHash(req.accountId(), req.txType(), req.amount(), req.occurredOn(), req.merchant());
        boolean duplicateExists = transactionRepository.existsByUserIdAndDedupHash(principal.userId(), dedupHash);
        if (duplicateExists && !Boolean.TRUE.equals(req.allowDuplicate())) {
            throw ApiException.conflict("POTENTIAL_DUPLICATE", "A transaction with the same account, type, amount, date, and merchant already exists.");
        }

        UUID resolvedCategoryId = req.categoryId();
        boolean essential = Boolean.TRUE.equals(req.essential());
        if (resolvedCategoryId != null) {
            TransactionCategoryEntity cat = categoryRepository.findById(resolvedCategoryId)
                    .filter(c -> c.isSystem() || principal.userId().equals(c.getUserId()))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Category not accessible"));
            if (req.essential() == null) {
                essential = cat.isEssential();
            }
        }

        Instant now = Instant.now();
        TransactionEntity tx = new TransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setUserId(principal.userId());
        tx.setAccountId(sourceAccount.getId());
        tx.setTransferAccountId("TRANSFER".equals(req.txType()) ? req.transferAccountId() : null);
        tx.setCategoryId(resolvedCategoryId);
        tx.setTxType(req.txType());
        tx.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        tx.setCurrency(req.currency() != null ? req.currency() : sourceAccount.getCurrency());
        tx.setOccurredOn(req.occurredOn());
        tx.setMerchant(req.merchant() != null ? req.merchant().trim() : null);
        tx.setDescription(req.description() != null ? req.description().trim() : null);
        tx.setEssential(essential);
        tx.setRecurring(Boolean.TRUE.equals(req.recurring()));
        tx.setRecurrenceRule(req.recurrenceRule() != null ? req.recurrenceRule() : "NONE");
        tx.setDedupHash(dedupHash);
        tx.setAnomalyStatus("NONE");
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        transactionRepository.save(tx);

        auditService.record(principal.userId(), principal.role(), "TRANSACTION_CREATED", "TRANSACTION", tx.getId().toString(), "SUCCESS", Map.of("txType", tx.getTxType()));

        Map<UUID, String> accountNames = accountRepository.findByUserIdOrderByCreatedAtAsc(principal.userId()).stream()
                .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getName));
        Map<UUID, String> categoryNames = categoryRepository.findAvailableForUser(principal.userId()).stream()
                .collect(Collectors.toMap(TransactionCategoryEntity::getId, TransactionCategoryEntity::getName));
        return toTransactionDto(tx, accountNames, categoryNames, duplicateExists);
    }

    @Transactional
    public LedgerDtos.TransactionDto updateTransaction(UserSessionPrincipal principal, UUID id, LedgerDtos.TransactionRequest req) {
        TransactionEntity tx = transactionRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        accountRepository.findByIdAndUserId(req.accountId(), principal.userId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));

        if ("TRANSFER".equals(req.txType()) && (req.transferAccountId() == null || req.transferAccountId().equals(req.accountId()))) {
            throw ApiException.badRequest("INVALID_TRANSFER", "Transfers require a distinct destination account");
        }

        tx.setAccountId(req.accountId());
        tx.setTransferAccountId("TRANSFER".equals(req.txType()) ? req.transferAccountId() : null);
        tx.setCategoryId(req.categoryId());
        tx.setTxType(req.txType());
        tx.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        if (req.currency() != null) tx.setCurrency(req.currency());
        tx.setOccurredOn(req.occurredOn());
        tx.setMerchant(req.merchant() != null ? req.merchant().trim() : null);
        tx.setDescription(req.description() != null ? req.description().trim() : null);
        if (req.essential() != null) tx.setEssential(req.essential());
        if (req.recurring() != null) tx.setRecurring(req.recurring());
        if (req.recurrenceRule() != null) tx.setRecurrenceRule(req.recurrenceRule());
        tx.setDedupHash(computeDedupHash(req.accountId(), req.txType(), req.amount(), req.occurredOn(), req.merchant()));
        tx.setUpdatedAt(Instant.now());
        transactionRepository.save(tx);

        auditService.record(principal.userId(), principal.role(), "TRANSACTION_UPDATED", "TRANSACTION", tx.getId().toString(), "SUCCESS", Map.of());
        Map<UUID, String> accountNames = accountRepository.findByUserIdOrderByCreatedAtAsc(principal.userId()).stream()
                .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getName));
        Map<UUID, String> categoryNames = categoryRepository.findAvailableForUser(principal.userId()).stream()
                .collect(Collectors.toMap(TransactionCategoryEntity::getId, TransactionCategoryEntity::getName));
        return toTransactionDto(tx, accountNames, categoryNames, false);
    }

    @Transactional
    public void deleteTransaction(UserSessionPrincipal principal, UUID id) {
        TransactionEntity tx = transactionRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        transactionRepository.delete(tx);
        auditService.record(principal.userId(), principal.role(), "TRANSACTION_DELETED", "TRANSACTION", id.toString(), "SUCCESS", Map.of());
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.ExpenseDto> listExpenses(UUID userId) {
        return expenseRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toExpenseDto)
                .toList();
    }

    @Transactional
    public LedgerDtos.ExpenseDto createExpense(UserSessionPrincipal principal, LedgerDtos.ExpenseRequest req) {
        Instant now = Instant.now();
        ExpenseEntity e = new ExpenseEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(principal.userId());
        e.setCategoryId(req.categoryId());
        e.setTitle(req.title().trim());
        e.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        e.setFrequency(req.frequency());
        e.setEssential(req.essential());
        e.setNextDueDate(req.nextDueDate());
        e.setActive(req.active() == null || req.active());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        expenseRepository.save(e);
        return toExpenseDto(e);
    }

    @Transactional
    public LedgerDtos.ExpenseDto updateExpense(UserSessionPrincipal principal, UUID id, LedgerDtos.ExpenseRequest req) {
        ExpenseEntity e = expenseRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Expense template not found"));
        e.setCategoryId(req.categoryId());
        e.setTitle(req.title().trim());
        e.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        e.setFrequency(req.frequency());
        e.setEssential(req.essential());
        e.setNextDueDate(req.nextDueDate());
        if (req.active() != null) e.setActive(req.active());
        e.setUpdatedAt(Instant.now());
        expenseRepository.save(e);
        return toExpenseDto(e);
    }

    @Transactional
    public void deleteExpense(UserSessionPrincipal principal, UUID id) {
        ExpenseEntity e = expenseRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Expense template not found"));
        expenseRepository.delete(e);
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.SavingsGoalDto> listGoals(UUID userId) {
        return savingsGoalRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .map(this::toGoalDto)
                .toList();
    }

    @Transactional
    public LedgerDtos.SavingsGoalDto createGoal(UserSessionPrincipal principal, LedgerDtos.SavingsGoalRequest req) {
        Instant now = Instant.now();
        SavingsGoalEntity g = new SavingsGoalEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(principal.userId());
        g.setName(req.name().trim());
        g.setTargetAmount(req.targetAmount().setScale(4, RoundingMode.HALF_UP));
        g.setCurrentAmount(req.currentAmount() != null ? req.currentAmount().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        g.setTargetDate(req.targetDate());
        g.setMonthlyContribution(req.monthlyContribution() != null ? req.monthlyContribution().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        g.setPriority(req.priority() != null ? req.priority() : 1);
        g.setStatus(req.status() != null ? req.status() : "ACTIVE");
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        savingsGoalRepository.save(g);
        return toGoalDto(g);
    }

    @Transactional
    public LedgerDtos.SavingsGoalDto updateGoal(UserSessionPrincipal principal, UUID id, LedgerDtos.SavingsGoalRequest req) {
        SavingsGoalEntity g = savingsGoalRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Savings goal not found"));
        g.setName(req.name().trim());
        g.setTargetAmount(req.targetAmount().setScale(4, RoundingMode.HALF_UP));
        if (req.currentAmount() != null) g.setCurrentAmount(req.currentAmount().setScale(4, RoundingMode.HALF_UP));
        g.setTargetDate(req.targetDate());
        if (req.monthlyContribution() != null) g.setMonthlyContribution(req.monthlyContribution().setScale(4, RoundingMode.HALF_UP));
        if (req.priority() != null) g.setPriority(req.priority());
        if (req.status() != null) g.setStatus(req.status());
        g.setUpdatedAt(Instant.now());
        savingsGoalRepository.save(g);
        return toGoalDto(g);
    }

    @Transactional
    public LedgerDtos.SavingsGoalDto contributeToGoal(UserSessionPrincipal principal, UUID id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Contribution must be positive");
        }
        SavingsGoalEntity g = savingsGoalRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Savings goal not found"));
        g.setCurrentAmount(g.getCurrentAmount().add(amount.setScale(4, RoundingMode.HALF_UP)));
        if (g.getCurrentAmount().compareTo(g.getTargetAmount()) >= 0) {
            g.setStatus("COMPLETED");
        }
        g.setUpdatedAt(Instant.now());
        savingsGoalRepository.save(g);
        return toGoalDto(g);
    }

    @Transactional
    public void deleteGoal(UserSessionPrincipal principal, UUID id) {
        SavingsGoalEntity g = savingsGoalRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Savings goal not found"));
        savingsGoalRepository.delete(g);
    }

    @Transactional(readOnly = true)
    public LedgerDtos.LedgerSummary computeSummary(UUID userId, LocalDate from, LocalDate to) {
        List<AccountEntity> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<TransactionEntity> allTxs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);

        List<LedgerDtos.AccountDto> accountDtos = accounts.stream()
                .map(acc -> toAccountDto(acc, computeAccountBalance(acc, allTxs)))
                .toList();

        BigDecimal totalBalance = accountDtos.stream()
                .map(LedgerDtos.AccountDto::currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal periodIncome = BigDecimal.ZERO;
        BigDecimal periodExpenses = BigDecimal.ZERO;
        BigDecimal periodSavings = BigDecimal.ZERO;

        long spanDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        LocalDate prevFrom = from.minusDays(spanDays);
        LocalDate prevTo = from.minusDays(1);
        BigDecimal prevIncome = BigDecimal.ZERO;
        BigDecimal prevExpenses = BigDecimal.ZERO;
        boolean hasPrevData = false;

        for (TransactionEntity tx : allTxs) {
            LocalDate d = tx.getOccurredOn();
            if (!d.isBefore(from) && !d.isAfter(to)) {
                switch (tx.getTxType()) {
                    case "INCOME" -> periodIncome = periodIncome.add(tx.getAmount());
                    case "EXPENSE" -> periodExpenses = periodExpenses.add(tx.getAmount());
                    case "REFUND" -> periodExpenses = periodExpenses.subtract(tx.getAmount());
                    case "SAVINGS" -> periodSavings = periodSavings.add(tx.getAmount());
                    default -> {} // TRANSFER does not affect income/expense totals
                }
            } else if (!d.isBefore(prevFrom) && !d.isAfter(prevTo)) {
                hasPrevData = true;
                switch (tx.getTxType()) {
                    case "INCOME" -> prevIncome = prevIncome.add(tx.getAmount());
                    case "EXPENSE" -> prevExpenses = prevExpenses.add(tx.getAmount());
                    case "REFUND" -> prevExpenses = prevExpenses.subtract(tx.getAmount());
                    default -> {}
                }
            }
        }

        if (periodExpenses.compareTo(BigDecimal.ZERO) < 0) periodExpenses = BigDecimal.ZERO;
        if (prevExpenses.compareTo(BigDecimal.ZERO) < 0) prevExpenses = BigDecimal.ZERO;

        return new LedgerDtos.LedgerSummary(
                totalBalance.setScale(2, RoundingMode.HALF_UP),
                periodIncome.setScale(2, RoundingMode.HALF_UP),
                periodExpenses.setScale(2, RoundingMode.HALF_UP),
                periodSavings.setScale(2, RoundingMode.HALF_UP),
                prevIncome.setScale(2, RoundingMode.HALF_UP),
                prevExpenses.setScale(2, RoundingMode.HALF_UP),
                hasPrevData,
                accountDtos
        );
    }

    public BigDecimal computeAccountBalance(AccountEntity account, List<TransactionEntity> allTxs) {
        BigDecimal balance = account.getOpeningBalance();
        for (TransactionEntity tx : allTxs) {
            if (tx.getOccurredOn().isBefore(account.getOpeningBalanceDate())) {
                continue;
            }
            if (account.getId().equals(tx.getAccountId())) {
                switch (tx.getTxType()) {
                    case "INCOME", "REFUND" -> balance = balance.add(tx.getAmount());
                    case "EXPENSE", "SAVINGS", "TRANSFER" -> balance = balance.subtract(tx.getAmount());
                    default -> {}
                }
            } else if (account.getId().equals(tx.getTransferAccountId()) && "TRANSFER".equals(tx.getTxType())) {
                balance = balance.add(tx.getAmount());
            }
        }
        return balance.setScale(2, RoundingMode.HALF_UP);
    }

    public String computeDedupHash(UUID accountId, String txType, BigDecimal amount, LocalDate occurredOn, String merchant) {
        String raw = accountId + "|" + txType + "|" + amount.setScale(2, RoundingMode.HALF_UP)
                + "|" + occurredOn + "|" + (merchant != null ? merchant.trim().toLowerCase() : "");
        return TokenUtil.sha256Hex(raw);
    }

    private LedgerDtos.AccountDto toAccountDto(AccountEntity acc, BigDecimal balance) {
        return new LedgerDtos.AccountDto(
                acc.getId(), acc.getName(), acc.getAccountType(), acc.getCurrency(),
                acc.getOpeningBalance().setScale(2, RoundingMode.HALF_UP), acc.getOpeningBalanceDate(),
                balance, acc.isArchived(), acc.getUpdatedAt()
        );
    }

    private LedgerDtos.IncomeSourceDto toIncomeDto(IncomeSourceEntity i) {
        return new LedgerDtos.IncomeSourceDto(
                i.getId(), i.getAccountId(), i.getName(), i.getAmount().setScale(2, RoundingMode.HALF_UP),
                i.getFrequency(), i.getExpectedDay(), i.getNextExpectedDate(), i.getStabilityScore(), i.isActive()
        );
    }

    private LedgerDtos.TransactionDto toTransactionDto(TransactionEntity tx,
                                                       Map<UUID, String> accountNames,
                                                       Map<UUID, String> categoryNames,
                                                       boolean duplicateDetected) {
        return new LedgerDtos.TransactionDto(
                tx.getId(), tx.getAccountId(), accountNames.getOrDefault(tx.getAccountId(), "Account"),
                tx.getTransferAccountId(), tx.getCategoryId(),
                tx.getCategoryId() != null ? categoryNames.getOrDefault(tx.getCategoryId(), "Uncategorized") : "Uncategorized",
                tx.getTxType(), tx.getAmount().setScale(2, RoundingMode.HALF_UP), tx.getCurrency(),
                tx.getOccurredOn(), tx.getMerchant(), tx.getDescription(), tx.isEssential(),
                tx.isRecurring(), tx.getRecurrenceRule(), tx.getAnomalyStatus(),
                tx.getSuggestedCategoryId(), tx.getLinkedPaymentRecordId(), duplicateDetected
        );
    }

    private LedgerDtos.ExpenseDto toExpenseDto(ExpenseEntity e) {
        return new LedgerDtos.ExpenseDto(
                e.getId(), e.getCategoryId(), e.getTitle(), e.getAmount().setScale(2, RoundingMode.HALF_UP),
                e.getFrequency(), e.isEssential(), e.getNextDueDate(), e.isActive()
        );
    }

    private LedgerDtos.SavingsGoalDto toGoalDto(SavingsGoalEntity g) {
        double pct = g.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? g.getCurrentAmount().divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        return new LedgerDtos.SavingsGoalDto(
                g.getId(), g.getName(), g.getTargetAmount().setScale(2, RoundingMode.HALF_UP),
                g.getCurrentAmount().setScale(2, RoundingMode.HALF_UP), g.getTargetDate(),
                g.getMonthlyContribution().setScale(2, RoundingMode.HALF_UP), g.getPriority(), g.getStatus(),
                Math.min(100.0, Math.round(pct * 10.0) / 10.0)
        );
    }
}
