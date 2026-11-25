package com.finsentinel.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.ledger.*;
import com.finsentinel.profile.FinancialProfileEntity;
import com.finsentinel.profile.FinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class PaymentPlannerService {

    private final FinancialObligationRepository obligationRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final FinancialProfileRepository profileRepository;
    private final LedgerService ledgerService;
    private final ForecastService forecastService;
    private final EmiCalculatorService emiCalculatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentPlannerService(FinancialObligationRepository obligationRepository,
                                 PaymentRecordRepository paymentRecordRepository,
                                 PaymentScheduleRepository scheduleRepository,
                                 AccountRepository accountRepository,
                                 TransactionRepository transactionRepository,
                                 TransactionCategoryRepository categoryRepository,
                                 FinancialProfileRepository profileRepository,
                                 LedgerService ledgerService,
                                 ForecastService forecastService,
                                 EmiCalculatorService emiCalculatorService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.obligationRepository = obligationRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.scheduleRepository = scheduleRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.profileRepository = profileRepository;
        this.ledgerService = ledgerService;
        this.forecastService = forecastService;
        this.emiCalculatorService = emiCalculatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.ObligationDto> listObligations(UUID userId) {
        List<FinancialObligationEntity> obligations = obligationRepository.findByUserIdOrderByDueDateAsc(userId);
        BigDecimal availableBalance = computeAvailableBalance(userId);
        BigDecimal reserve = resolveReserveThreshold(userId);

        // Organize upcoming obligations via Java PriorityQueue: overdue/earliest due date -> essential -> higher penalty/amount
        PriorityQueue<FinancialObligationEntity> pq = new PriorityQueue<>(obligationComparator());
        pq.addAll(obligations);

        Map<UUID, Integer> rankMap = new HashMap<>();
        int rank = 1;
        while (!pq.isEmpty()) {
            rankMap.put(pq.poll().getId(), rank++);
        }

        BigDecimal running = availableBalance;
        List<PaymentDtos.ObligationDto> dtos = new ArrayList<>();
        for (FinancialObligationEntity ob : obligations) {
            BigDecimal rem = ob.getRemainingAmount();
            String affordability;
            BigDecimal impact = BigDecimal.ZERO;
            if ("PAID".equals(ob.getStatus()) || rem.compareTo(BigDecimal.ZERO) == 0) {
                affordability = "SETTLED";
            } else {
                running = running.subtract(rem);
                impact = rem.negate();
                if (running.compareTo(BigDecimal.ZERO) < 0) {
                    affordability = "SHORTFALL";
                } else if (running.compareTo(reserve) < 0) {
                    affordability = "RESERVE_PRESSURE";
                } else {
                    affordability = "AFFORDABLE";
                }
            }
            dtos.add(toDto(ob, rankMap.getOrDefault(ob.getId(), 99), affordability, impact));
        }
        return dtos;
    }

    @Transactional
    public PaymentDtos.ObligationDto createObligation(UserSessionPrincipal principal, PaymentDtos.ObligationRequest req) {
        if (req.accountId() != null) {
            accountRepository.findByIdAndUserId(req.accountId(), principal.userId())
                    .orElseThrow(() -> ApiException.notFound("Account not found"));
        }
        Instant now = Instant.now();
        FinancialObligationEntity ob = new FinancialObligationEntity();
        ob.setId(UUID.randomUUID());
        ob.setUserId(principal.userId());
        ob.setAccountId(req.accountId());
        ob.setTitle(req.title().trim());
        ob.setCategory(req.category().trim());
        ob.setTotalAmount(req.totalAmount().setScale(4, RoundingMode.HALF_UP));
        ob.setPaidAmount(BigDecimal.ZERO);
        ob.setDueDate(req.dueDate());
        ob.setFrequency(req.frequency() != null ? req.frequency() : "ONE_TIME");
        ob.setEssential(req.essential());
        ob.setStatus(req.dueDate().isBefore(LocalDate.now()) ? "OVERDUE" : "UPCOMING");
        ob.setLateFee(req.lateFee() != null ? req.lateFee().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        ob.setGraceDays(req.graceDays() != null ? req.graceDays() : 0);
        ob.setInterestRate(req.interestRate() != null ? req.interestRate() : BigDecimal.ZERO);
        ob.setNotes(req.notes() != null ? req.notes().trim() : null);
        ob.setCreatedAt(now);
        ob.setUpdatedAt(now);
        obligationRepository.save(ob);
        auditService.record(principal.userId(), principal.role(), "OBLIGATION_CREATED", "FINANCIAL_OBLIGATION", ob.getId().toString(), "SUCCESS", Map.of());
        return toDto(ob, 1, "UPCOMING", ob.getTotalAmount().negate());
    }

    @Transactional
    public PaymentDtos.ObligationDto updateObligation(UserSessionPrincipal principal, UUID id, PaymentDtos.ObligationRequest req) {
        FinancialObligationEntity ob = obligationRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Obligation not found"));
        ob.setAccountId(req.accountId());
        ob.setTitle(req.title().trim());
        ob.setCategory(req.category().trim());
        ob.setTotalAmount(req.totalAmount().setScale(4, RoundingMode.HALF_UP));
        ob.setDueDate(req.dueDate());
        if (req.frequency() != null) ob.setFrequency(req.frequency());
        ob.setEssential(req.essential());
        if (req.lateFee() != null) ob.setLateFee(req.lateFee().setScale(4, RoundingMode.HALF_UP));
        if (req.graceDays() != null) ob.setGraceDays(req.graceDays());
        if (req.interestRate() != null) ob.setInterestRate(req.interestRate());
        ob.setNotes(req.notes() != null ? req.notes().trim() : null);
        recomputeStatus(ob);
        ob.setUpdatedAt(Instant.now());
        obligationRepository.save(ob);
        return toDto(ob, 1, "UPDATED", ob.getRemainingAmount().negate());
    }

    @Transactional
    public PaymentDtos.ObligationDto updateStatus(UserSessionPrincipal principal, UUID id, String status) {
        if (!List.of("UPCOMING", "PARTIAL", "PAID", "OVERDUE").contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be UPCOMING, PARTIAL, PAID, or OVERDUE");
        }
        FinancialObligationEntity ob = obligationRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Obligation not found"));
        ob.setStatus(status);
        if ("PAID".equals(status)) {
            ob.setPaidAmount(ob.getTotalAmount());
        }
        ob.setUpdatedAt(Instant.now());
        obligationRepository.save(ob);
        return toDto(ob, 1, status, ob.getRemainingAmount().negate());
    }

    @Transactional
    public void deleteObligation(UserSessionPrincipal principal, UUID id) {
        FinancialObligationEntity ob = obligationRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Obligation not found"));
        obligationRepository.delete(ob);
    }

    @Transactional
    public PaymentDtos.PaymentRecordDto recordManualPayment(UserSessionPrincipal principal, UUID obligationId, PaymentDtos.RecordPaymentRequest req) {
        FinancialObligationEntity ob = obligationRepository.findByIdAndUserId(obligationId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Obligation not found"));

        BigDecimal remaining = ob.getRemainingAmount();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.conflict("ALREADY_SETTLED", "This obligation is already fully settled.");
        }
        if (req.amount().compareTo(remaining) > 0) {
            throw ApiException.badRequest("OVERPAYMENT", "Payment amount (" + req.amount() + ") exceeds remaining obligation balance (" + remaining + ").");
        }

        UUID linkedTxId = null;
        UUID recordId = UUID.randomUUID();
        if (req.recordLedgerTransaction()) {
            UUID accountId = req.accountId() != null ? req.accountId() : ob.getAccountId();
            if (accountId == null) {
                accountId = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(principal.userId())
                        .stream().findFirst().map(AccountEntity::getId)
                        .orElseThrow(() -> ApiException.badRequest("NO_ACCOUNT", "Create or specify an account to record a ledger transaction."));
            }
            TransactionEntity tx = new TransactionEntity();
            tx.setId(UUID.randomUUID());
            tx.setUserId(principal.userId());
            tx.setAccountId(accountId);
            tx.setCategoryId(UUID.fromString("11111111-1111-1111-1111-111111111107")); // Loan & EMI Payment
            tx.setTxType("EXPENSE");
            tx.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
            tx.setCurrency("INR");
            tx.setOccurredOn(req.paidOn());
            tx.setMerchant(ob.getTitle());
            tx.setDescription("Manual payment record (" + req.paymentMethod() + ")");
            tx.setEssential(ob.isEssential());
            tx.setRecurring(false);
            tx.setRecurrenceRule("NONE");
            tx.setLinkedPaymentRecordId(recordId);
            tx.setAnomalyStatus("NONE");
            tx.setCreatedAt(Instant.now());
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);
            linkedTxId = tx.getId();
        }

        PaymentRecordEntity rec = new PaymentRecordEntity();
        rec.setId(recordId);
        rec.setUserId(principal.userId());
        rec.setObligationId(ob.getId());
        rec.setAccountId(req.accountId() != null ? req.accountId() : ob.getAccountId());
        rec.setTransactionId(linkedTxId);
        rec.setAmount(req.amount().setScale(4, RoundingMode.HALF_UP));
        rec.setPaidOn(req.paidOn());
        rec.setPaymentMethod(req.paymentMethod());
        rec.setReferenceNote(req.referenceNote());
        rec.setCreatedAt(Instant.now());
        paymentRecordRepository.save(rec);

        ob.setPaidAmount(ob.getPaidAmount().add(req.amount()).setScale(4, RoundingMode.HALF_UP));
        recomputeStatus(ob);
        ob.setUpdatedAt(Instant.now());
        obligationRepository.save(ob);

        auditService.record(principal.userId(), principal.role(), "MANUAL_PAYMENT_RECORDED", "PAYMENT_RECORD", rec.getId().toString(), "SUCCESS", Map.of("linkedTx", linkedTxId != null));

        return new PaymentDtos.PaymentRecordDto(
                rec.getId(), rec.getObligationId(), rec.getAccountId(), rec.getTransactionId(),
                rec.getAmount().setScale(2, RoundingMode.HALF_UP), rec.getPaidOn(), rec.getPaymentMethod(),
                rec.getReferenceNote(), rec.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentRecordDto> listPaymentRecords(UUID userId) {
        return paymentRecordRepository.findByUserIdOrderByPaidOnDescCreatedAtDesc(userId).stream()
                .map(rec -> new PaymentDtos.PaymentRecordDto(
                        rec.getId(), rec.getObligationId(), rec.getAccountId(), rec.getTransactionId(),
                        rec.getAmount().setScale(2, RoundingMode.HALF_UP), rec.getPaidOn(),
                        rec.getPaymentMethod(), rec.getReferenceNote(), rec.getCreatedAt()))
                .toList();
    }

    @Transactional
    public PaymentDtos.PaymentPlanComparisonResponse comparePaymentSchedules(UUID userId, int horizonDays, Map<UUID, LocalDate> customHypotheticalDates) {
        int safeHorizon = Math.max(7, Math.min(180, horizonDays));
        LocalDate start = LocalDate.now();
        BigDecimal availableBalance = computeAvailableBalance(userId);
        BigDecimal reserve = resolveReserveThreshold(userId);

        List<FinancialObligationEntity> unpaid = obligationRepository.findByUserIdOrderByDueDateAsc(userId).stream()
                .filter(o -> !"PAID".equals(o.getStatus()) && o.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        ForecastDtos.ForecastResultDto baselineForecast = forecastService.generateForecast(userId, safeHorizon, start, false);
        Map<LocalDate, BigDecimal> incomeCreditsByDate = extractIncomeCreditsByDate(baselineForecast);

        List<PaymentDtos.ScenarioScheduleDto> scenarios = new ArrayList<>();
        scenarios.add(buildSchedule("ON_TIME_CONTRACTUAL", "Contractual Due-Date Schedule", unpaid, availableBalance, reserve, incomeCreditsByDate, start, safeHorizon, Map.of(), false));
        scenarios.add(buildSchedule("RESERVE_SAFE_STAGGERED", "Reserve-Preserving Staggered Simulation", unpaid, availableBalance, reserve, incomeCreditsByDate, start, safeHorizon, Map.of(), true));
        scenarios.add(buildEssentialFirstSchedule(unpaid, availableBalance, reserve, incomeCreditsByDate, start, safeHorizon));

        if (customHypotheticalDates != null && !customHypotheticalDates.isEmpty()) {
            scenarios.add(buildSchedule("CUSTOM_HYPOTHETICAL", "Custom Drag-and-Drop Simulation", unpaid, availableBalance, reserve, incomeCreditsByDate, start, safeHorizon, customHypotheticalDates, false));
        }

        // Persist latest contractual schedule summary
        PaymentDtos.ScenarioScheduleDto primary = scenarios.get(0);
        PaymentScheduleEntity saved = new PaymentScheduleEntity();
        saved.setId(UUID.randomUUID());
        saved.setUserId(userId);
        saved.setScenarioName(primary.scenarioName());
        saved.setStrategy(primary.strategy());
        saved.setHorizonDays(safeHorizon);
        saved.setFeasible(primary.feasible());
        saved.setShortfallAmount(primary.shortfallAmount());
        saved.setMinProjectedBalance(primary.minProjectedBalance());
        saved.setPayloadJson(toJson(primary));
        saved.setCreatedAt(Instant.now());
        scheduleRepository.save(saved);

        return new PaymentDtos.PaymentPlanComparisonResponse(
                availableBalance,
                reserve,
                safeHorizon,
                "Hypothetical schedules are planning simulations only. Contractual due dates never change without provider consent, and mandatory bills are never omitted.",
                scenarios
        );
    }

    public PaymentDtos.MethodComparisonResponse compareMethods(UUID userId, PaymentDtos.MethodComparisonInput input) {
        return emiCalculatorService.comparePaymentMethods(input, computeAvailableBalance(userId), resolveReserveThreshold(userId));
    }

    private PaymentDtos.ScenarioScheduleDto buildSchedule(
            String strategy,
            String name,
            List<FinancialObligationEntity> unpaid,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            Map<LocalDate, BigDecimal> incomeCreditsByDate,
            LocalDate start,
            int horizonDays,
            Map<UUID, LocalDate> overrides,
            boolean staggerNonEssential) {

        LocalDate end = start.plusDays(horizonDays);
        List<FinancialObligationEntity> ordered = new ArrayList<>(unpaid);
        ordered.sort(obligationComparator());

        Map<UUID, LocalDate> plannedDates = new HashMap<>();
        BigDecimal totalLateFees = BigDecimal.ZERO;

        for (FinancialObligationEntity ob : ordered) {
            LocalDate baseDue = ob.getDueDate().isBefore(start) ? start : ob.getDueDate();
            LocalDate target = overrides.getOrDefault(ob.getId(), baseDue);
            if (staggerNonEssential && !ob.isEssential() && ob.getGraceDays() > 0) {
                // Shift non-essential obligation within contractual grace window toward next income date if helpful
                LocalDate shifted = target.plusDays(Math.min(ob.getGraceDays(), 7));
                if (!shifted.isAfter(end)) {
                    target = shifted;
                }
            }
            if (target.isAfter(ob.getDueDate().plusDays(ob.getGraceDays()))) {
                totalLateFees = totalLateFees.add(ob.getLateFee() != null ? ob.getLateFee() : BigDecimal.ZERO);
            }
            plannedDates.put(ob.getId(), target);
        }

        return evaluatePlan(strategy, name, ordered, plannedDates, openingBalance, reserveThreshold, incomeCreditsByDate, start, end, totalLateFees);
    }

    private PaymentDtos.ScenarioScheduleDto buildEssentialFirstSchedule(
            List<FinancialObligationEntity> unpaid,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            Map<LocalDate, BigDecimal> incomeCreditsByDate,
            LocalDate start,
            int horizonDays) {

        LocalDate end = start.plusDays(horizonDays);
        PriorityQueue<FinancialObligationEntity> pq = new PriorityQueue<>(
                Comparator.comparing(FinancialObligationEntity::isEssential).reversed()
                        .thenComparing(FinancialObligationEntity::getDueDate)
                        .thenComparing(FinancialObligationEntity::getLateFee, Comparator.reverseOrder())
        );
        pq.addAll(unpaid);

        List<FinancialObligationEntity> ordered = new ArrayList<>();
        while (!pq.isEmpty()) ordered.add(pq.poll());

        Map<UUID, LocalDate> plannedDates = new HashMap<>();
        for (FinancialObligationEntity ob : ordered) {
            plannedDates.put(ob.getId(), ob.getDueDate().isBefore(start) ? start : ob.getDueDate());
        }

        return evaluatePlan("ESSENTIAL_FIRST_PRIORITY", "Essential-First Priority Queue Order", ordered, plannedDates, openingBalance, reserveThreshold, incomeCreditsByDate, start, end, BigDecimal.ZERO);
    }

    private PaymentDtos.ScenarioScheduleDto evaluatePlan(
            String strategy,
            String name,
            List<FinancialObligationEntity> ordered,
            Map<UUID, LocalDate> plannedDates,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            Map<LocalDate, BigDecimal> incomeCreditsByDate,
            LocalDate start,
            LocalDate end,
            BigDecimal totalLateFees) {

        Map<LocalDate, List<FinancialObligationEntity>> byDate = new HashMap<>();
        for (FinancialObligationEntity ob : ordered) {
            LocalDate d = plannedDates.getOrDefault(ob.getId(), ob.getDueDate());
            if (!d.isAfter(end)) {
                byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(ob);
            }
        }

        BigDecimal running = openingBalance.setScale(2, RoundingMode.HALF_UP);
        BigDecimal minBalance = running;
        BigDecimal maxShortfall = BigDecimal.ZERO;
        List<PaymentDtos.ScheduledObligationItem> items = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal credit = incomeCreditsByDate.getOrDefault(d, BigDecimal.ZERO);
            running = running.add(credit);

            List<FinancialObligationEntity> dayObs = byDate.getOrDefault(d, List.of());
            for (FinancialObligationEntity ob : dayObs) {
                BigDecimal rem = ob.getRemainingAmount().setScale(2, RoundingMode.HALF_UP);
                boolean shifted = !d.equals(ob.getDueDate()) && ob.getDueDate().isAfter(start);
                BigDecimal penalty = d.isAfter(ob.getDueDate().plusDays(ob.getGraceDays())) && ob.getLateFee() != null
                        ? ob.getLateFee().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                running = running.subtract(rem).subtract(penalty);
                if (running.compareTo(minBalance) < 0) minBalance = running;
                if (running.compareTo(BigDecimal.ZERO) < 0) {
                    maxShortfall = maxShortfall.max(running.abs());
                }
                String note = running.compareTo(BigDecimal.ZERO) < 0
                        ? "Insufficient projected funds — explicit shortfall of " + running.abs()
                        : (running.compareTo(reserveThreshold) < 0 ? "Breaches reserve threshold" : "Covered within reserve");
                items.add(new PaymentDtos.ScheduledObligationItem(
                        ob.getId(), ob.getTitle(), ob.getDueDate(), d, shifted, ob.isEssential(),
                        rem, running, penalty, note));
            }
        }

        boolean feasible = maxShortfall.compareTo(BigDecimal.ZERO) == 0;
        boolean respectsReserve = minBalance.compareTo(reserveThreshold) >= 0;
        List<String> consequences = new ArrayList<>();
        if (!feasible) {
            consequences.add("NO FEASIBLE SCHEDULE WITHOUT ADDITIONAL LIQUIDITY: Projected shortfall of " + maxShortfall + ". Mandatory obligations cannot be covered on current schedule.");
        } else if (!respectsReserve) {
            consequences.add("Schedule avoids negative balance but dips below reserve threshold (" + reserveThreshold + ") to " + minBalance + ".");
        } else {
            consequences.add("All upcoming obligations covered while preserving reserve threshold (min balance " + minBalance + ").");
        }
        if (totalLateFees.compareTo(BigDecimal.ZERO) > 0) {
            consequences.add("Includes estimated late fee assumptions of " + totalLateFees + " for shifted dates beyond grace period.");
        }

        return new PaymentDtos.ScenarioScheduleDto(
                strategy, name, feasible, respectsReserve, maxShortfall.setScale(2, RoundingMode.HALF_UP),
                minBalance.setScale(2, RoundingMode.HALF_UP), totalLateFees.setScale(2, RoundingMode.HALF_UP),
                consequences, items
        );
    }

    public static Comparator<FinancialObligationEntity> obligationComparator() {
        return Comparator.comparing(FinancialObligationEntity::getDueDate)
                .thenComparing(FinancialObligationEntity::isEssential, Comparator.reverseOrder())
                .thenComparing(FinancialObligationEntity::getLateFee, Comparator.reverseOrder())
                .thenComparing(FinancialObligationEntity::getTotalAmount, Comparator.reverseOrder())
                .thenComparing(FinancialObligationEntity::getId);
    }

    private Map<LocalDate, BigDecimal> extractIncomeCreditsByDate(ForecastDtos.ForecastResultDto forecast) {
        Map<LocalDate, BigDecimal> credits = new HashMap<>();
        for (ForecastDtos.DailyProjectionPoint pt : forecast.dailyPoints()) {
            BigDecimal sum = pt.events().stream()
                    .filter(e -> e.kind() == ForecastDtos.EventKind.INCOME)
                    .map(ForecastDtos.CashFlowEvent::signedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                credits.put(pt.date(), sum);
            }
        }
        return credits;
    }

    private BigDecimal computeAvailableBalance(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<TransactionEntity> txs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        BigDecimal total = BigDecimal.ZERO;
        for (AccountEntity acc : accounts) {
            if (!"CREDIT_CARD".equals(acc.getAccountType())) {
                total = total.add(ledgerService.computeAccountBalance(acc, txs));
            }
        }
        if (accounts.isEmpty()) {
            FinancialProfileEntity p = profileRepository.findById(userId).orElse(null);
            if (p != null && p.getLiquidFunds() != null) total = p.getLiquidFunds();
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveReserveThreshold(UUID userId) {
        return profileRepository.findById(userId)
                .map(FinancialProfileEntity::getReserveThreshold)
                .orElse(new BigDecimal("15000.00"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void recomputeStatus(FinancialObligationEntity ob) {
        if (ob.getPaidAmount().compareTo(ob.getTotalAmount()) >= 0) {
            ob.setStatus("PAID");
        } else if (ob.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            ob.setStatus("PARTIAL");
        } else if (ob.getDueDate().isBefore(LocalDate.now())) {
            ob.setStatus("OVERDUE");
        } else {
            ob.setStatus("UPCOMING");
        }
    }

    private PaymentDtos.ObligationDto toDto(FinancialObligationEntity ob, int rank, String affordability, BigDecimal impact) {
        return new PaymentDtos.ObligationDto(
                ob.getId(), ob.getAccountId(), ob.getTitle(), ob.getCategory(),
                ob.getTotalAmount().setScale(2, RoundingMode.HALF_UP),
                ob.getPaidAmount().setScale(2, RoundingMode.HALF_UP),
                ob.getRemainingAmount().setScale(2, RoundingMode.HALF_UP),
                ob.getDueDate(), ob.getFrequency(), ob.isEssential(), ob.getStatus(),
                ob.getLateFee().setScale(2, RoundingMode.HALF_UP), ob.getGraceDays(),
                ob.getInterestRate(), ob.getNotes(), rank, affordability, impact
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
