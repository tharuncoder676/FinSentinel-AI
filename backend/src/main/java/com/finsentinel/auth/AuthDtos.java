package com.finsentinel.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must include at least one letter and one number")
            String password
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Token is required")
            String token,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must include at least one letter and one number")
            String newPassword
    ) {}

    public record VerifyEmailRequest(
            @NotBlank(message = "Verification token is required")
            String token
    ) {}

    public record UpdateProfileUserRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120)
            String fullName
    ) {}

    public record UpdatePreferencesRequest(
            @Pattern(regexp = "light|dark|system", message = "Theme must be light, dark, or system")
            String theme,
            @Pattern(regexp = "en|ta", message = "Language must be en or ta")
            String language,
            @Pattern(regexp = "INR|USD|EUR|GBP", message = "Unsupported currency")
            String currency,
            @Size(min = 2, max = 16)
            String locale,
            @Size(min = 2, max = 64)
            String timezone,
            Boolean aiExternalConsent,
            Boolean reducedMotion
    ) {}

    public record UserPreferencesDto(
            String theme,
            String language,
            String currency,
            String locale,
            String timezone,
            boolean aiExternalConsent,
            boolean reducedMotion
    ) {}

    public record AuthUserDto(
            UUID id,
            String email,
            String fullName,
            String role,
            boolean emailVerified,
            boolean demoUser,
            boolean onboardingCompleted,
            UserPreferencesDto preferences,
            Instant createdAt
    ) {}

    public record GenericMessageResponse(String message) {}
}
