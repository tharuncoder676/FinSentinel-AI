package com.finsentinel.profile;

import com.finsentinel.auth.AuthDtos;
import com.finsentinel.auth.AuthService;
import com.finsentinel.auth.UserEntity;
import com.finsentinel.auth.UserRepository;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class ProfileService {

    private final FinancialProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;

    public ProfileService(FinancialProfileRepository profileRepository,
                          UserRepository userRepository,
                          AuthService authService,
                          AuditService auditService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.auditService = auditService;
    }

    public record FinancialProfileDto(
            BigDecimal monthlyIncome,
            BigDecimal otherIncome,
            BigDecimal regularExpenses,
            BigDecimal liquidFunds,
            BigDecimal reserveThreshold,
            String incomeFrequency,
            boolean onboardingCompleted,
            boolean onboardingSkipped
    ) {}

    public record OnboardingRequest(
            @DecimalMin("0.00") BigDecimal monthlyIncome,
            @DecimalMin("0.00") BigDecimal otherIncome,
            @DecimalMin("0.00") BigDecimal regularExpenses,
            @DecimalMin("0.00") BigDecimal liquidFunds,
            @DecimalMin("0.00") BigDecimal reserveThreshold,
            @Pattern(regexp = "MONTHLY|BIWEEKLY|WEEKLY|IRREGULAR") String incomeFrequency,
            boolean skipped
    ) {}

    @Transactional(readOnly = true)
    public FinancialProfileDto getProfile(UserSessionPrincipal principal) {
        FinancialProfileEntity entity = profileRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Financial profile not found"));
        return toDto(entity);
    }

    @Transactional
    public FinancialProfileDto saveOnboarding(UserSessionPrincipal principal, @Valid OnboardingRequest req) {
        Instant now = Instant.now();
        FinancialProfileEntity entity = profileRepository.findById(principal.userId())
                .orElseGet(() -> {
                    FinancialProfileEntity p = new FinancialProfileEntity();
                    p.setUserId(principal.userId());
                    p.setCreatedAt(now);
                    return p;
                });
        if (req.monthlyIncome() != null) entity.setMonthlyIncome(req.monthlyIncome());
        if (req.otherIncome() != null) entity.setOtherIncome(req.otherIncome());
        if (req.regularExpenses() != null) entity.setRegularExpenses(req.regularExpenses());
        if (req.liquidFunds() != null) entity.setLiquidFunds(req.liquidFunds());
        if (req.reserveThreshold() != null) entity.setReserveThreshold(req.reserveThreshold());
        if (req.incomeFrequency() != null) entity.setIncomeFrequency(req.incomeFrequency());
        entity.setOnboardingSkipped(req.skipped());
        entity.setOnboardingCompleted(true);
        entity.setUpdatedAt(now);
        profileRepository.save(entity);
        auditService.record(principal.userId(), principal.role(), "ONBOARDING_SAVED", "FINANCIAL_PROFILE", principal.userId().toString(), "SUCCESS", Map.of("skipped", req.skipped()));
        return toDto(entity);
    }

    @Transactional
    public AuthDtos.AuthUserDto enterDemoWorkspace(HttpServletRequest request, HttpServletResponse response) {
        UserEntity demoUser = userRepository.findByEmailIgnoreCase("demo@finsentinel.local")
                .orElseThrow(() -> ApiException.notFound("Demo workspace is not seeded"));
        authService.createSession(demoUser, request, response);
        auditService.record(demoUser.getId(), demoUser.getRole(), "DEMO_WORKSPACE_ENTERED", "SESSION", demoUser.getId().toString(), "SUCCESS", Map.of());
        return authService.getCurrentUser(new com.finsentinel.common.security.UserSessionPrincipal(
                demoUser.getId(), demoUser.getEmail(), demoUser.getFullName(), demoUser.getRole(),
                demoUser.isDemoUser(), demoUser.isEmailVerified(), null));
    }

    private FinancialProfileDto toDto(FinancialProfileEntity e) {
        return new FinancialProfileDto(
                e.getMonthlyIncome(),
                e.getOtherIncome(),
                e.getRegularExpenses(),
                e.getLiquidFunds(),
                e.getReserveThreshold(),
                e.getIncomeFrequency(),
                e.isOnboardingCompleted(),
                e.isOnboardingSkipped()
        );
    }
}
