package com.finsentinel.auth;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.config.AppProperties;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.ledger.*;
import com.finsentinel.notification.NotificationPreferencesEntity;
import com.finsentinel.notification.NotificationPreferencesRepository;
import com.finsentinel.notification.NotificationService;
import com.finsentinel.payment.FinancialObligationEntity;
import com.finsentinel.payment.FinancialObligationRepository;
import com.finsentinel.profile.FinancialProfileEntity;
import com.finsentinel.profile.FinancialProfileRepository;
import com.finsentinel.risk.RiskEngineService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoSeedService {

    public static final String DEMO_EMAIL = "demo@finsentinel.local";
    public static final String ADMIN_EMAIL = "admin@finsentinel.local";

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final FinancialProfileRepository profileRepository;
    private final AccountRepository accountRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialObligationRepository obligationRepository;
    private final NotificationPreferencesRepository notificationPrefsRepository;
    private final RiskEngineService riskEngineService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public DemoSeedService(AppProperties appProperties,
                           UserRepository userRepository,
                           UserPreferencesRepository preferencesRepository,
                           FinancialProfileRepository profileRepository,
                           AccountRepository accountRepository,
                           IncomeSourceRepository incomeSourceRepository,
                           TransactionRepository transactionRepository,
                           ExpenseRepository expenseRepository,
                           SavingsGoalRepository savingsGoalRepository,
                           FinancialObligationRepository obligationRepository,
                           NotificationPreferencesRepository notificationPrefsRepository,
                           RiskEngineService riskEngineService,
                           NotificationService notificationService,
                           PasswordEncoder passwordEncoder) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.profileRepository = profileRepository;
        this.accountRepository = accountRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.transactionRepository = transactionRepository;
        this.expenseRepository = expenseRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.obligationRepository = obligationRepository;
        this.notificationPrefsRepository = notificationPrefsRepository;
        this.riskEngineService = riskEngineService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        if (!appProperties.demo().seedEnabled()) return;
        seedAdminUser();
        if (!userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            seedDemoAccount();
        }
    }

    @Transactional
    public void resetDemoAccount() {
        userRepository.findByEmailIgnoreCase(DEMO_EMAIL).ifPresent(existing -> {
            UUID uid = existing.getId();
            transactionRepository.deleteAll(transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(uid));
            obligationRepository.deleteAll(obligationRepository.findByUserIdOrderByDueDateAsc(uid));
            expenseRepository.deleteAll(expenseRepository.findByUserIdOrderByCreatedAtAsc(uid));
            incomeSourceRepository.deleteAll(incomeSourceRepository.findByUserIdOrderByCreatedAtAsc(uid));
            savingsGoalRepository.deleteAll(savingsGoalRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(uid));
            accountRepository.deleteAll(accountRepository.findByUserIdOrderByCreatedAtAsc(uid));
            populateDemoData(uid);
        });
        if (!userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            seedDemoAccount();
        }
    }

    private void seedAdminUser() {
        if (userRepository.existsByEmailIgnoreCase(ADMIN_EMAIL)) return;
        Instant now = Instant.now();
        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail(ADMIN_EMAIL);
        admin.setFullName("Operations Administrator");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass#2026"));
        admin.setRole("ADMIN");
        admin.setEmailVerified(true);
        admin.setDemoUser(false);
        admin.setStatus("ACTIVE");
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);

        UserPreferencesEntity prefs = new UserPreferencesEntity();
        prefs.setUserId(admin.getId());
        prefs.setUpdatedAt(now);
        preferencesRepository.save(prefs);
    }

    private void seedDemoAccount() {
        Instant now = Instant.now();
        UserEntity demo = new UserEntity();
        demo.setId(UUID.randomUUID());
        demo.setEmail(DEMO_EMAIL);
        demo.setFullName("Aarav Krishnan (Demo Profile)");
        demo.setPasswordHash(passwordEncoder.encode("DemoPass#2026"));
        demo.setRole("USER");
        demo.setEmailVerified(true);
        demo.setDemoUser(true);
        demo.setStatus("ACTIVE");
        demo.setCreatedAt(now);
        demo.setUpdatedAt(now);
        userRepository.save(demo);

        UserPreferencesEntity prefs = new UserPreferencesEntity();
        prefs.setUserId(demo.getId());
        prefs.setCurrency("INR");
        prefs.setLocale("en-IN");
        prefs.setTimezone("Asia/Kolkata");
        prefs.setUpdatedAt(now);
        preferencesRepository.save(prefs);

        NotificationPreferencesEntity np = new NotificationPreferencesEntity();
        np.setUserId(demo.getId());
        np.setInAppEnabled(true);
        np.setEmailEnabled(false);
        np.setReminderDaysBefore(5);
        np.setShortfallAlertsEnabled(true);
        np.setUpdatedAt(now);
        notificationPrefsRepository.save(np);

        populateDemoData(demo.getId());
    }

    private void populateDemoData(UUID userId) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        LocalDate openDate = today.minusDays(95);

        FinancialProfileEntity profile = profileRepository.findById(userId).orElseGet(() -> {
            FinancialProfileEntity p = new FinancialProfileEntity();
            p.setUserId(userId);
            p.setCreatedAt(now);
            return p;
        });
        profile.setMonthlyIncome(new BigDecimal("92000.0000"));
        profile.setOtherIncome(new BigDecimal("12000.0000"));
        profile.setRegularExpenses(new BigDecimal("54000.0000"));
        profile.setLiquidFunds(new BigDecimal("68000.0000"));
        profile.setReserveThreshold(new BigDecimal("25000.0000"));
        profile.setIncomeFrequency("MONTHLY");
        profile.setOnboardingCompleted(true);
        profile.setOnboardingSkipped(false);
        profile.setUpdatedAt(now);
        profileRepository.save(profile);

        // Two tracked accounts
        AccountEntity primary = new AccountEntity();
        primary.setId(UUID.randomUUID());
        primary.setUserId(userId);
        primary.setName("HDFC Primary Salary Account");
        primary.setAccountType("CHECKING");
        primary.setCurrency("INR");
        primary.setOpeningBalance(new BigDecimal("42000.0000"));
        primary.setOpeningBalanceDate(openDate);
        primary.setCreatedAt(now);
        primary.setUpdatedAt(now);
        accountRepository.save(primary);

        AccountEntity reserveAcc = new AccountEntity();
        reserveAcc.setId(UUID.randomUUID());
        reserveAcc.setUserId(userId);
        reserveAcc.setName("SBI Emergency Reserve");
        reserveAcc.setAccountType("SAVINGS");
        reserveAcc.setCurrency("INR");
        reserveAcc.setOpeningBalance(new BigDecimal("35000.0000"));
        reserveAcc.setOpeningBalanceDate(openDate);
        reserveAcc.setCreatedAt(now);
        reserveAcc.setUpdatedAt(now);
        accountRepository.save(reserveAcc);

        // Income sources
        IncomeSourceEntity salary = new IncomeSourceEntity();
        salary.setId(UUID.randomUUID());
        salary.setUserId(userId);
        salary.setAccountId(primary.getId());
        salary.setName("Primary Technology Salary");
        salary.setAmount(new BigDecimal("92000.0000"));
        salary.setFrequency("MONTHLY");
        salary.setExpectedDay(1);
        salary.setNextExpectedDate(today.plusDays(6));
        salary.setStabilityScore(new BigDecimal("0.9400"));
        salary.setActive(true);
        salary.setCreatedAt(now);
        salary.setUpdatedAt(now);
        incomeSourceRepository.save(salary);

        IncomeSourceEntity freelance = new IncomeSourceEntity();
        freelance.setId(UUID.randomUUID());
        freelance.setUserId(userId);
        freelance.setAccountId(primary.getId());
        freelance.setName("UI Advisory Retainer");
        freelance.setAmount(new BigDecimal("12000.0000"));
        freelance.setFrequency("MONTHLY");
        freelance.setExpectedDay(15);
        freelance.setNextExpectedDate(today.plusDays(18));
        freelance.setStabilityScore(new BigDecimal("0.7200"));
        freelance.setActive(true);
        freelance.setCreatedAt(now);
        freelance.setUpdatedAt(now);
        incomeSourceRepository.save(freelance);

        // Historical transactions across 3 months (exact reproducible fixture)
        UUID catSalary = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID catRent = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID catUtil = UUID.fromString("11111111-1111-1111-1111-111111111104");
        UUID catGroc = UUID.fromString("11111111-1111-1111-1111-111111111105");
        UUID catEmi = UUID.fromString("11111111-1111-1111-1111-111111111107");
        UUID catDin = UUID.fromString("11111111-1111-1111-1111-111111111109");
        UUID catSav = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID catTr = UUID.fromString("11111111-1111-1111-1111-111111111112");

        saveTx(userId, primary.getId(), null, catSalary, "INCOME", "92000", today.minusDays(84), "Apex Systems Payroll", "Monthly salary", false);
        saveTx(userId, primary.getId(), null, catRent, "EXPENSE", "22000", today.minusDays(81), "Prestige Estates", "Monthly apartment rent", true);
        saveTx(userId, primary.getId(), null, catEmi, "EXPENSE", "18500", today.minusDays(78), "HDFC Auto Loan EMI", "Vehicle installment", true);
        saveTx(userId, primary.getId(), null, catGroc, "EXPENSE", "8200", today.minusDays(70), "BigBasket Fresh", "Household groceries", true);
        saveTx(userId, primary.getId(), null, catSalary, "INCOME", "92000", today.minusDays(54), "Apex Systems Payroll", "Monthly salary", false);
        saveTx(userId, primary.getId(), null, catRent, "EXPENSE", "22000", today.minusDays(51), "Prestige Estates", "Monthly apartment rent", true);
        saveTx(userId, primary.getId(), null, catEmi, "EXPENSE", "18500", today.minusDays(48), "HDFC Auto Loan EMI", "Vehicle installment", true);
        saveTx(userId, primary.getId(), null, catDin, "EXPENSE", "5400", today.minusDays(40), "Swiggy Dining", "Weekend family dinner", false);
        saveTx(userId, primary.getId(), null, catSalary, "INCOME", "92000", today.minusDays(24), "Apex Systems Payroll", "Monthly salary", false);
        saveTx(userId, primary.getId(), null, catRent, "EXPENSE", "22000", today.minusDays(21), "Prestige Estates", "Monthly apartment rent", true);
        saveTx(userId, primary.getId(), null, catEmi, "EXPENSE", "18500", today.minusDays(18), "HDFC Auto Loan EMI", "Vehicle installment", true);
        saveTx(userId, primary.getId(), null, catUtil, "EXPENSE", "3450", today.minusDays(14), "BESCOM & Airtel Fiber", "Electricity & internet", true);
        saveTx(userId, primary.getId(), null, catGroc, "EXPENSE", "9100", today.minusDays(10), "Zepto & DMart", "Monthly groceries", true);
        saveTx(userId, primary.getId(), null, catDin, "EXPENSE", "19800", today.minusDays(6), "Croma Electronics", "Home appliance replacement", false);
        saveTx(userId, primary.getId(), reserveAcc.getId(), catTr, "TRANSFER", "8000", today.minusDays(5), "Internal Reserve Transfer", "Monthly buffer sweep", false);
        saveTx(userId, primary.getId(), null, catSav, "SAVINGS", "5000", today.minusDays(4), "Emergency Fund Deposit", "Goal contribution", true);

        // Recurring expense templates
        saveExpense(userId, catGroc, "Monthly Groceries & Pantry", "9000", "MONTHLY", true, today.plusDays(9));
        saveExpense(userId, catUtil, "Electricity, Water & Fiber", "3500", "MONTHLY", true, today.plusDays(12));
        saveExpense(userId, catDin, "Dining & Weekend Outings", "5500", "MONTHLY", false, today.plusDays(14));

        // Savings goals
        SavingsGoalEntity g1 = new SavingsGoalEntity();
        g1.setId(UUID.randomUUID());
        g1.setUserId(userId);
        g1.setName("6-Month Emergency Runway");
        g1.setTargetAmount(new BigDecimal("250000.0000"));
        g1.setCurrentAmount(new BigDecimal("92000.0000"));
        g1.setTargetDate(today.plusMonths(12));
        g1.setMonthlyContribution(new BigDecimal("8000.0000"));
        g1.setPriority(1);
        g1.setStatus("ACTIVE");
        g1.setCreatedAt(now);
        g1.setUpdatedAt(now);
        savingsGoalRepository.save(g1);

        SavingsGoalEntity g2 = new SavingsGoalEntity();
        g2.setId(UUID.randomUUID());
        g2.setUserId(userId);
        g2.setName("Annual Health Insurance Corpus");
        g2.setTargetAmount(new BigDecimal("45000.0000"));
        g2.setCurrentAmount(new BigDecimal("27000.0000"));
        g2.setTargetDate(today.plusMonths(5));
        g2.setMonthlyContribution(new BigDecimal("3500.0000"));
        g2.setPriority(2);
        g2.setStatus("ACTIVE");
        g2.setCreatedAt(now);
        g2.setUpdatedAt(now);
        savingsGoalRepository.save(g2);

        // Upcoming obligations (designed so salary delay + unexpected expense shows real impact!)
        saveObligation(userId, primary.getId(), "Apartment Rent — Prestige Estates", "Housing", "22000", "0", today.plusDays(4), "MONTHLY", true, "UPCOMING", "500", 3);
        saveObligation(userId, primary.getId(), "HDFC Auto Loan EMI", "Loan EMI", "18500", "0", today.plusDays(8), "MONTHLY", true, "UPCOMING", "750", 2);
        saveObligation(userId, primary.getId(), "Credit Card Statement — Axis", "Credit Card", "14200", "4200", today.plusDays(11), "MONTHLY", true, "PARTIAL", "600", 5);
        saveObligation(userId, primary.getId(), "Quarterly Broadband & OTT Bundle", "Utilities", "4800", "0", today.plusDays(16), "QUARTERLY", false, "UPCOMING", "150", 7);
        saveObligation(userId, primary.getId(), "Annual Term Insurance Premium", "Insurance", "16500", "0", today.plusDays(22), "ANNUAL", true, "UPCOMING", "0", 10);

        // Precompute initial risk & notifications
        riskEngineService.assessAndPersist(userId, today);
        notificationService.evaluateRemindersForUser(userId);
    }

    private void saveTx(UUID userId, UUID accId, UUID transferAccId, UUID catId, String type, String amt,
                        LocalDate date, String merchant, String desc, boolean essential) {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setUserId(userId);
        tx.setAccountId(accId);
        tx.setTransferAccountId(transferAccId);
        tx.setCategoryId(catId);
        tx.setTxType(type);
        tx.setAmount(new BigDecimal(amt));
        tx.setCurrency("INR");
        tx.setOccurredOn(date);
        tx.setMerchant(merchant);
        tx.setDescription(desc);
        tx.setEssential(essential);
        tx.setRecurring(false);
        tx.setRecurrenceRule("NONE");
        tx.setAnomalyStatus("NONE");
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        transactionRepository.save(tx);
    }

    private void saveExpense(UUID userId, UUID catId, String title, String amt, String freq, boolean essential, LocalDate due) {
        ExpenseEntity e = new ExpenseEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setCategoryId(catId);
        e.setTitle(title);
        e.setAmount(new BigDecimal(amt));
        e.setFrequency(freq);
        e.setEssential(essential);
        e.setNextDueDate(due);
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        expenseRepository.save(e);
    }

    private void saveObligation(UUID userId, UUID accId, String title, String cat, String total, String paid,
                                LocalDate due, String freq, boolean essential, String status, String lateFee, int graceDays) {
        FinancialObligationEntity ob = new FinancialObligationEntity();
        ob.setId(UUID.randomUUID());
        ob.setUserId(userId);
        ob.setAccountId(accId);
        ob.setTitle(title);
        ob.setCategory(cat);
        ob.setTotalAmount(new BigDecimal(total));
        ob.setPaidAmount(new BigDecimal(paid));
        ob.setDueDate(due);
        ob.setFrequency(freq);
        ob.setEssential(essential);
        ob.setStatus(status);
        ob.setLateFee(new BigDecimal(lateFee));
        ob.setGraceDays(graceDays);
        ob.setInterestRate(BigDecimal.ZERO);
        ob.setCreatedAt(Instant.now());
        ob.setUpdatedAt(Instant.now());
        obligationRepository.save(ob);
    }
}

@RestController
@RequestMapping("/api/v1/demo")
class DemoResetController {

    private final DemoSeedService demoSeedService;

    DemoResetController(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @PostMapping("/reset")
    public Map<String, String> resetDemo(@AuthenticationPrincipal UserSessionPrincipal principal) {
        if (!principal.demoUser() && !"ADMIN".equals(principal.role())) {
            throw ApiException.forbidden("Only the demo workspace or an administrator can reset demo fixtures.");
        }
        demoSeedService.resetDemoAccount();
        return Map.of("status", "DEMO_DATASET_RESET");
    }
}
