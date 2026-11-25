package com.finsentinel.auth;

import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthUserDto> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                         HttpServletRequest httpRequest,
                                                         HttpServletResponse httpResponse) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, httpRequest, httpResponse));
    }

    @PostMapping("/login")
    public AuthDtos.AuthUserDto login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    public AuthDtos.GenericMessageResponse logout(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                  HttpServletResponse httpResponse) {
        authService.logout(principal, httpResponse);
        return new AuthDtos.GenericMessageResponse("Signed out successfully");
    }

    @PostMapping("/verify-email")
    public AuthDtos.GenericMessageResponse verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return new AuthDtos.GenericMessageResponse("Email address verified successfully");
    }

    @PostMapping("/resend-verification")
    public AuthDtos.GenericMessageResponse resendVerification(@AuthenticationPrincipal UserSessionPrincipal principal) {
        authService.resendVerification(principal);
        return new AuthDtos.GenericMessageResponse("Verification message dispatched if account requires verification");
    }

    @PostMapping("/forgot-password")
    public AuthDtos.GenericMessageResponse forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return new AuthDtos.GenericMessageResponse("If an account matches that email, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public AuthDtos.GenericMessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new AuthDtos.GenericMessageResponse("Password has been reset. Please sign in with your new password.");
    }

    @GetMapping("/me")
    public AuthDtos.AuthUserDto me(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return authService.getCurrentUser(principal);
    }

    @GetMapping("/session-status")
    public Map<String, Object> sessionStatus(@AuthenticationPrincipal UserSessionPrincipal principal) {
        if (principal == null) {
            return Map.of("authenticated", false);
        }
        return Map.of("authenticated", true, "user", authService.getCurrentUser(principal));
    }

    @PatchMapping("/profile")
    public AuthDtos.AuthUserDto updateUserProfile(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                  @Valid @RequestBody AuthDtos.UpdateProfileUserRequest request) {
        return authService.updateProfileUser(principal, request);
    }

    @PatchMapping("/preferences")
    public AuthDtos.UserPreferencesDto updatePreferences(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                         @Valid @RequestBody AuthDtos.UpdatePreferencesRequest request) {
        return authService.updatePreferences(principal, request);
    }

    @GetMapping("/export")
    public Map<String, Object> exportUserData(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return authService.exportUserData(principal);
    }

    @DeleteMapping("/account")
    public AuthDtos.GenericMessageResponse deleteAccount(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                         HttpServletResponse response) {
        authService.deleteAccount(principal, response);
        return new AuthDtos.GenericMessageResponse("Account deleted");
    }
}
