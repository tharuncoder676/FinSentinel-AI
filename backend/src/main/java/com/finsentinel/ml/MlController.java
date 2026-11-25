package com.finsentinel.ml;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ml")
public class MlController {

    private final MlBaselineService mlService;

    public MlController(MlBaselineService mlService) {
        this.mlService = mlService;
    }

    @GetMapping("/insights")
    public MlBaselineService.MlInsightsResponse getInsights(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return mlService.analyzeUser(principal.userId());
    }

    @PostMapping("/anomalies/{txId}/confirm")
    public Map<String, String> confirmAnomaly(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID txId) {
        mlService.updateAnomalyStatus(principal, txId, "CONFIRMED");
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/anomalies/{txId}/dismiss")
    public Map<String, String> dismissAnomaly(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID txId) {
        mlService.updateAnomalyStatus(principal, txId, "DISMISSED");
        return Map.of("status", "DISMISSED");
    }

    @PostMapping("/categories/{txId}/correct")
    public Map<String, String> correctCategory(@AuthenticationPrincipal UserSessionPrincipal principal,
                                               @PathVariable UUID txId,
                                               @RequestBody Map<String, UUID> body) {
        mlService.applyCategoryCorrection(principal, txId, body.get("categoryId"));
        return Map.of("status", "UPDATED");
    }
}
