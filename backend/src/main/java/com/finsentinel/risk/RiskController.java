package com.finsentinel.risk;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskEngineService riskEngineService;

    public RiskController(RiskEngineService riskEngineService) {
        this.riskEngineService = riskEngineService;
    }

    @GetMapping("/assessment")
    public RiskDtos.RiskAssessmentDto getAssessment(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return riskEngineService.getLatestOrCompute(principal.userId());
    }

    @PostMapping("/assessment/recalculate")
    public RiskDtos.RiskAssessmentDto recalculate(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return riskEngineService.getLatestOrCompute(principal.userId());
    }

    @GetMapping("/history")
    public List<RiskDtos.RiskHistoryPoint> getHistory(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return riskEngineService.getHistory(principal.userId());
    }
}
