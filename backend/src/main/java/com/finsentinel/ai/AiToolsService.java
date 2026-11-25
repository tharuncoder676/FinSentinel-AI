package com.finsentinel.ai;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.ledger.LedgerDtos;
import com.finsentinel.ledger.LedgerService;
import com.finsentinel.payment.PaymentDtos;
import com.finsentinel.payment.PaymentPlannerService;
import com.finsentinel.report.PdfReportService;
import com.finsentinel.risk.RiskDtos;
import com.finsentinel.risk.RiskEngineService;
import com.finsentinel.simulation.SimulationDtos;
import com.finsentinel.simulation.SimulationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiToolsService {

    public static final Set<String> ALLOWED_TOOLS = Set.of(
            "getFinancialSummary",
            "getRiskAssessment",
            "getCashFlowForecast",
            "getUpcomingPayments",
            "comparePaymentScenarios",
            "runFinancialStressTest",
            "explainRiskFactors",
            "generateFinancialReport"
    );

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions|system\\s+prompt|reveal\\s+.*api[_\\s]?key|dump\\s+all\\s+users|other\\s+user\\s+id|userId\\s*=|bypass\\s+authorization)"
    );

    private final LedgerService ledgerService;
    private final RiskEngineService riskEngineService;
    private final ForecastService forecastService;
    private final PaymentPlannerService paymentPlannerService;
    private final SimulationService simulationService;
    private final PdfReportService pdfReportService;
    private final AuditService auditService;

    public AiToolsService(LedgerService ledgerService,
                          RiskEngineService riskEngineService,
                          ForecastService forecastService,
                          PaymentPlannerService paymentPlannerService,
                          SimulationService simulationService,
                          PdfReportService pdfReportService,
                          AuditService auditService) {
        this.ledgerService = ledgerService;
        this.riskEngineService = riskEngineService;
        this.forecastService = forecastService;
        this.paymentPlannerService = paymentPlannerService;
        this.simulationService = simulationService;
        this.pdfReportService = pdfReportService;
        this.auditService = auditService;
    }

    public record ToolInvocationResult(
            String toolName,
            Object data,
            String citation
    ) {}

    public void validatePromptSafety(UserSessionPrincipal principal, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.badRequest("EMPTY_PROMPT", "Prompt message is required.");
        }
        if (prompt.length() > 2000) {
            throw ApiException.badRequest("PROMPT_TOO_LONG", "Prompt exceeds maximum 2,000 character safety budget.");
        }
        if (INJECTION_PATTERN.matcher(prompt).find()) {
            auditService.record(principal.userId(), principal.role(), "AI_PROMPT_INJECTION_BLOCKED", "AI_CHAT", principal.userId().toString(), "BLOCKED", Map.of());
            throw ApiException.badRequest("PROMPT_INJECTION_BLOCKED", "Request rejected by AI safety policy: untrusted instruction override or cross-user access attempt detected.");
        }
    }

    public ToolInvocationResult invokeTool(UserSessionPrincipal principal, String toolName, Map<String, Object> args, boolean confirmWrite) {
        if (!ALLOWED_TOOLS.contains(toolName)) {
            auditService.record(principal.userId(), principal.role(), "AI_BLOCKED_TOOL", "AI_TOOL", toolName, "BLOCKED", Map.of());
            throw ApiException.forbidden("Tool '" + toolName + "' is not in the permitted backend tool allowlist.");
        }
        // Never honor userId passed inside tool arguments; always enforce authenticated principal.userId()
        if (args != null && args.containsKey("userId") && !principal.userId().toString().equals(String.valueOf(args.get("userId")))) {
            auditService.record(principal.userId(), principal.role(), "AI_CROSS_USER_TOOL_BLOCKED", "AI_TOOL", toolName, "BLOCKED", Map.of());
            throw ApiException.forbidden("Cross-user tool invocation is prohibited.");
        }

        UUID userId = principal.userId();
        LocalDate today = LocalDate.now();

        return switch (toolName) {
            case "getFinancialSummary" -> {
                LedgerDtos.LedgerSummary summary = ledgerService.computeSummary(userId, today.minusDays(30), today);
                yield new ToolInvocationResult(toolName, summary, "Ledger summary (" + today.minusDays(30) + " to " + today + ")");
            }
            case "getRiskAssessment" -> {
                RiskDtos.RiskAssessmentDto risk = riskEngineService.getLatestOrCompute(userId);
                yield new ToolInvocationResult(toolName, risk, "Explainable risk policy " + risk.policyVersion());
            }
            case "getCashFlowForecast" -> {
                int horizon = clampInt(args != null ? args.get("horizonDays") : null, 90, 7, 365);
                ForecastDtos.ForecastResultDto forecast = forecastService.generateForecast(userId, horizon, today, false);
                yield new ToolInvocationResult(toolName, forecast, "Forecast " + forecast.modelVersion() + " (" + horizon + "d)");
            }
            case "getUpcomingPayments" -> {
                List<PaymentDtos.ObligationDto> payments = paymentPlannerService.listObligations(userId);
                yield new ToolInvocationResult(toolName, payments, "Financial obligations (" + payments.size() + " records)");
            }
            case "comparePaymentScenarios" -> {
                int horizon = clampInt(args != null ? args.get("horizonDays") : null, 60, 7, 180);
                PaymentDtos.PaymentPlanComparisonResponse plan = paymentPlannerService.comparePaymentSchedules(userId, horizon, Map.of());
                yield new ToolInvocationResult(toolName, plan, "Adaptive payment scenarios (" + horizon + "d)");
            }
            case "runFinancialStressTest" -> {
                int horizon = clampInt(args != null ? args.get("horizonDays") : null, 90, 7, 365);
                int delay = clampInt(args != null ? args.get("incomeDelayDays") : null, 15, 0, 90);
                BigDecimal unexpected = clampDecimal(args != null ? args.get("unexpectedExpenseAmount") : null, new BigDecimal("25000"));
                SimulationDtos.SimulationParameters params = new SimulationDtos.SimulationParameters(
                        "AI Tool Stress Simulation", "STRESS_TEST", "AI_SANDBOX", horizon,
                        BigDecimal.ONE, delay, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        unexpected, today.plusDays(10), 0, Map.of(), null, false
                );
                SimulationDtos.SimulationResponse sim = simulationService.simulate(userId, params, today);
                yield new ToolInvocationResult(toolName, sim, "Stress simulation (" + sim.modelVersion() + ")");
            }
            case "explainRiskFactors" -> {
                RiskDtos.RiskAssessmentDto risk = riskEngineService.getLatestOrCompute(userId);
                yield new ToolInvocationResult(toolName, risk.factors(), "Risk factor breakdown (" + risk.policyVersion() + ")");
            }
            case "generateFinancialReport" -> {
                if (!confirmWrite) {
                    throw ApiException.badRequest("CONFIRMATION_REQUIRED", "Generating and saving a PDF report modifies stored report history and requires explicit confirmation.");
                }
                String type = args != null && args.get("reportType") != null ? String.valueOf(args.get("reportType")) : "COMPREHENSIVE";
                PdfReportService.ReportSummaryDto report = pdfReportService.generateReport(principal, new PdfReportService.GenerateReportRequest(type, null, 90));
                yield new ToolInvocationResult(toolName, report, "Generated PDF report " + report.fileName());
            }
            default -> throw ApiException.forbidden("Unknown tool");
        };
    }

    private int clampInt(Object raw, int def, int min, int max) {
        if (raw == null) return def;
        try {
            int val = Integer.parseInt(String.valueOf(raw));
            return Math.max(min, Math.min(max, val));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private BigDecimal clampDecimal(Object raw, BigDecimal def) {
        if (raw == null) return def;
        try {
            BigDecimal val = new BigDecimal(String.valueOf(raw));
            if (val.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
            if (val.compareTo(new BigDecimal("10000000")) > 0) return new BigDecimal("10000000");
            return val;
        } catch (Exception e) {
            return def;
        }
    }
}
