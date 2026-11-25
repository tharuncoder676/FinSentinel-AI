package com.finsentinel.auth;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.config.AppProperties;
import com.finsentinel.common.security.TokenUtil;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.profile.FinancialProfileEntity;
import com.finsentinel.profile.FinancialProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailNotificationService mailService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public AuthService(UserRepository userRepository,
                       AuthSessionRepository sessionRepository,
                       VerificationTokenRepository verificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       UserPreferencesRepository preferencesRepository,
                       FinancialProfileRepository financialProfileRepository,
                       PasswordEncoder passwordEncoder,
                       MailNotificationService mailService,
                       AuditService auditService,
                       AppProperties appProperties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.preferencesRepository = preferencesRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Transactional
    public AuthDtos.AuthUserDto register(AuthDtos.RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        String normalizedEmail = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Unable to register with these credentials. Try signing in or resetting your password.");
        }

        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(normalizedEmail);
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole("USER");
        user.setEmailVerified(false);
        user.setDemoUser(false);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        UserPreferencesEntity prefs = new UserPreferencesEntity();
        prefs.setUserId(user.getId());
        prefs.setUpdatedAt(now);
        preferencesRepository.save(prefs);

        FinancialProfileEntity profile = new FinancialProfileEntity();
        profile.setUserId(user.getId());
        profile.setReserveThreshold(new BigDecimal("15000.0000"));
        profile.setIncomeFrequency("MONTHLY");
        profile.setOnboardingCompleted(false);
        profile.setOnboardingSkipped(false);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        financialProfileRepository.save(profile);

        issueVerificationToken(user);
        createSession(user, request, response);
        auditService.record(user.getId(), user.getRole(), "USER_REGISTERED", "USER", user.getId().toString(), "SUCCESS", Map.of());
        return buildUserDto(user, prefs, profile);
    }

    @Transactional
    public AuthDtos.AuthUserDto login(AuthDtos.LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String normalizedEmail = req.email().trim().toLowerCase();
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> {
                    auditService.record(null, "ANONYMOUS", "LOGIN_FAILED", "AUTH", normalizedEmail, "FAILURE", Map.of("reason", "invalid_credentials"));
                    return ApiException.unauthorized("Invalid email or password");
                });

        if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            auditService.record(user.getId(), user.getRole(), "LOGIN_FAILED", "AUTH", user.getId().toString(), "FAILURE", Map.of("reason", "invalid_credentials"));
            throw ApiException.unauthorized("Invalid email or password");
        }

        createSession(user, request, response);
        auditService.record(user.getId(), user.getRole(), "LOGIN_SUCCESS", "SESSION", user.getId().toString(), "SUCCESS", Map.of());
        UserPreferencesEntity prefs = preferencesRepository.findById(user.getId()).orElseGet(() -> createDefaultPrefs(user.getId()));
        FinancialProfileEntity profile = financialProfileRepository.findById(user.getId()).orElse(null);
        return buildUserDto(user, prefs, profile);
    }

    @Transactional
    public void logout(UserSessionPrincipal principal, HttpServletResponse response) {
        if (principal != null && principal.sessionId() != null) {
            sessionRepository.findById(principal.sessionId()).ifPresent(session -> {
                session.setRevoked(true);
                sessionRepository.save(session);
            });
            auditService.record(principal.userId(), principal.role(), "LOGOUT", "SESSION", principal.sessionId().toString(), "SUCCESS", Map.of());
        }
        clearSessionCookie(response);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = TokenUtil.sha256Hex(rawToken);
        VerificationTokenEntity token = verificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Verification token is invalid or expired"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("TOKEN_EXPIRED", "Verification token has already been used or expired");
        }
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.notFound("User account not found"));
        token.setUsed(true);
        verificationTokenRepository.save(token);
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(user.getId(), user.getRole(), "EMAIL_VERIFIED", "USER", user.getId().toString(), "SUCCESS", Map.of());
    }

    @Transactional
    public void resendVerification(UserSessionPrincipal principal) {
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) {
            return;
        }
        issueVerificationToken(user);
        auditService.record(user.getId(), user.getRole(), "VERIFICATION_RESENT", "USER", user.getId().toString(), "SUCCESS", Map.of());
    }

    @Transactional
    public void forgotPassword(String email) {
        // Non-enumerating: always succeed publicly regardless of whether email exists
        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase());
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            if ("ACTIVE".equals(user.getStatus())) {
                String rawToken = TokenUtil.generateToken();
                PasswordResetTokenEntity reset = new PasswordResetTokenEntity();
                reset.setId(UUID.randomUUID());
                reset.setUserId(user.getId());
                reset.setTokenHash(TokenUtil.sha256Hex(rawToken));
                reset.setExpiresAt(Instant.now().plus(Duration.ofMinutes(60)));
                reset.setUsed(false);
                reset.setCreatedAt(Instant.now());
                passwordResetTokenRepository.save(reset);
                mailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), rawToken);
                auditService.record(user.getId(), user.getRole(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString(), "SUCCESS", Map.of());
            }
        }
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest req) {
        String hash = TokenUtil.sha256Hex(req.token());
        PasswordResetTokenEntity token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Password reset token is invalid or expired"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("TOKEN_EXPIRED", "Password reset token has already been used or expired");
        }
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.notFound("User account not found"));
        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        sessionRepository.revokeAllForUser(user.getId());
        auditService.record(user.getId(), user.getRole(), "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString(), "SUCCESS", Map.of());
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthUserDto getCurrentUser(UserSessionPrincipal principal) {
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Session invalid"));
        UserPreferencesEntity prefs = preferencesRepository.findById(user.getId())
                .orElseGet(() -> createDefaultPrefs(user.getId()));
        FinancialProfileEntity profile = financialProfileRepository.findById(user.getId()).orElse(null);
        return buildUserDto(user, prefs, profile);
    }

    @Transactional
    public AuthDtos.AuthUserDto updateProfileUser(UserSessionPrincipal principal, AuthDtos.UpdateProfileUserRequest req) {
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setFullName(req.fullName().trim());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        UserPreferencesEntity prefs = preferencesRepository.findById(user.getId())
                .orElseGet(() -> createDefaultPrefs(user.getId()));
        FinancialProfileEntity profile = financialProfileRepository.findById(user.getId()).orElse(null);
        auditService.record(user.getId(), user.getRole(), "USER_PROFILE_UPDATED", "USER", user.getId().toString(), "SUCCESS", Map.of());
        return buildUserDto(user, prefs, profile);
    }

    @Transactional
    public AuthDtos.UserPreferencesDto updatePreferences(UserSessionPrincipal principal, AuthDtos.UpdatePreferencesRequest req) {
        UserPreferencesEntity prefs = preferencesRepository.findById(principal.userId())
                .orElseGet(() -> createDefaultPrefs(principal.userId()));
        if (req.theme() != null) prefs.setTheme(req.theme());
        if (req.language() != null) prefs.setLanguage(req.language());
        if (req.currency() != null) prefs.setCurrency(req.currency());
        if (req.locale() != null) prefs.setLocale(req.locale());
        if (req.timezone() != null) prefs.setTimezone(req.timezone());
        if (req.aiExternalConsent() != null) prefs.setAiExternalConsent(req.aiExternalConsent());
        if (req.reducedMotion() != null) prefs.setReducedMotion(req.reducedMotion());
        prefs.setUpdatedAt(Instant.now());
        preferencesRepository.save(prefs);
        auditService.record(principal.userId(), principal.role(), "PREFERENCES_UPDATED", "USER_PREFERENCES", principal.userId().toString(), "SUCCESS", Map.of());
        return toPreferencesDto(prefs);
    }

    @Transactional
    public void deleteAccount(UserSessionPrincipal principal, HttpServletResponse response) {
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isDemoUser()) {
            throw ApiException.badRequest("DEMO_ACCOUNT_PROTECTED", "The shared demo workspace account cannot be deleted.");
        }
        UUID userId = user.getId();
        sessionRepository.revokeAllForUser(userId);
        auditService.record(null, principal.role(), "ACCOUNT_DELETED", "USER", userId.toString(), "SUCCESS", Map.of());
        userRepository.delete(user);
        clearSessionCookie(response);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UserSessionPrincipal principal) {
        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        UserPreferencesEntity prefs = preferencesRepository.findById(user.getId()).orElse(null);
        FinancialProfileEntity profile = financialProfileRepository.findById(user.getId()).orElse(null);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("user", Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole(),
                "emailVerified", user.isEmailVerified(),
                "createdAt", user.getCreatedAt()
        ));
        export.put("preferences", prefs != null ? toPreferencesDto(prefs) : Map.of());
        export.put("financialProfile", profile != null ? Map.of(
                "monthlyIncome", profile.getMonthlyIncome() != null ? profile.getMonthlyIncome() : BigDecimal.ZERO,
                "reserveThreshold", profile.getReserveThreshold(),
                "incomeFrequency", profile.getIncomeFrequency()
        ) : Map.of());
        auditService.record(user.getId(), user.getRole(), "DATA_EXPORTED", "USER", user.getId().toString(), "SUCCESS", Map.of());
        return export;
    }

    public void createSession(UserEntity user, HttpServletRequest request, HttpServletResponse response) {
        String rawToken = TokenUtil.generateToken();
        Instant now = Instant.now();
        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setTokenHash(TokenUtil.sha256Hex(rawToken));
        session.setExpiresAt(now.plus(Duration.ofMinutes(appProperties.security().sessionTtlMinutes())));
        session.setRevoked(false);
        session.setUserAgent(request.getHeader("User-Agent"));
        session.setIpAddress(request.getRemoteAddr());
        session.setCreatedAt(now);
        sessionRepository.save(session);

        ResponseCookie cookie = ResponseCookie.from(appProperties.security().sessionCookieName(), rawToken)
                .httpOnly(true)
                .secure(appProperties.security().sessionCookieSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(appProperties.security().sessionTtlMinutes()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(appProperties.security().sessionCookieName(), "")
                .httpOnly(true)
                .secure(appProperties.security().sessionCookieSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void issueVerificationToken(UserEntity user) {
        String rawToken = TokenUtil.generateToken();
        VerificationTokenEntity token = new VerificationTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setTokenHash(TokenUtil.sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        token.setUsed(false);
        token.setCreatedAt(Instant.now());
        verificationTokenRepository.save(token);
        mailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
    }

    private UserPreferencesEntity createDefaultPrefs(UUID userId) {
        UserPreferencesEntity prefs = new UserPreferencesEntity();
        prefs.setUserId(userId);
        prefs.setUpdatedAt(Instant.now());
        return prefs;
    }

    private AuthDtos.AuthUserDto buildUserDto(UserEntity user, UserPreferencesEntity prefs, FinancialProfileEntity profile) {
        return new AuthDtos.AuthUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isEmailVerified(),
                user.isDemoUser(),
                profile != null && profile.isOnboardingCompleted(),
                toPreferencesDto(prefs),
                user.getCreatedAt()
        );
    }

    private AuthDtos.UserPreferencesDto toPreferencesDto(UserPreferencesEntity prefs) {
        return new AuthDtos.UserPreferencesDto(
                prefs.getTheme(),
                prefs.getLanguage(),
                prefs.getCurrency(),
                prefs.getLocale(),
                prefs.getTimezone(),
                prefs.isAiExternalConsent(),
                prefs.isReducedMotion()
        );
    }
}
