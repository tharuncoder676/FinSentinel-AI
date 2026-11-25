package com.finsentinel.profile;

import com.finsentinel.auth.AuthDtos;
import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ProfileService.FinancialProfileDto getProfile(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return profileService.getProfile(principal);
    }

    @PutMapping("/profile")
    public ProfileService.FinancialProfileDto updateProfile(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                            @Valid @RequestBody ProfileService.OnboardingRequest request) {
        return profileService.saveOnboarding(principal, request);
    }

    @PostMapping("/onboarding")
    public ProfileService.FinancialProfileDto submitOnboarding(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                               @Valid @RequestBody ProfileService.OnboardingRequest request) {
        return profileService.saveOnboarding(principal, request);
    }

    @PostMapping("/public/demo-session")
    public AuthDtos.AuthUserDto enterDemoWorkspace(HttpServletRequest request, HttpServletResponse response) {
        return profileService.enterDemoWorkspace(request, response);
    }
}
