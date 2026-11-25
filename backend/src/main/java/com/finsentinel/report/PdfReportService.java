package com.finsentinel.report;

import com.finsentinel.auth.UserEntity;
import com.finsentinel.auth.UserRepository;
import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.forecast.ForecastDtos;
import com.finsentinel.forecast.ForecastService;
import com.finsentinel.ledger.LedgerDtos;
import com.finsentinel.ledger.LedgerService;
import com.finsentinel.payment.PaymentDtos;
import com.finsentinel.payment.PaymentPlannerService;
import com.finsentinel.risk.RiskDtos;
import com.finsentinel.risk.RiskEngineService;
import com.finsentinel.simulation.SimulationDtos;
import com.finsentinel.simulation.SimulationService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PdfReportService {

    private final GeneratedReportRepository reportRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final RiskEngineService riskEngineService;
    private final ForecastService forecastService;
    private final PaymentPlannerService paymentPlannerService;
    private final SimulationService simulationService;
    private final AuditService auditService;

    public PdfReportService(GeneratedReportRepository reportRepository,
                            UserRepository userRepository,
                            LedgerService ledgerService,
                            RiskEngineService riskEngineService,
                            ForecastService forecastService,
                            PaymentPlannerService paymentPlannerService,
                            SimulationService simulationService,
                            AuditService auditService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.riskEngineService = riskEngineService;
        this.forecastService = forecastService;
        this.paymentPlannerService = paymentPlannerService;
        this.simulationService = simulationService;
        this.auditService = auditService;
    }

    public record ReportSummaryDto(
            UUID id,
            String reportType,
            String title,
            String fileName,
            String status,
            String policyVersion,
            Instant createdAt
    ) {}

    public record GenerateReportRequest(
            String reportType,
            UUID scenarioId,
            Integer horizonDays
    ) {}

    @Transactional
    public ReportSummaryDto generateReport(UserSessionPrincipal principal, GenerateReportRequest req) {
        String type = req.reportType() != null ? req.reportType().toUpperCase() : "COMPREHENSIVE";
        int horizon = req.horizonDays() != null ? Math.max(7, Math.min(365, req.horizonDays())) : 90;

        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        LocalDate today = LocalDate.now();

        LedgerDtos.LedgerSummary ledger = ledgerService.computeSummary(user.getId(), today.minusDays(30), today);
        RiskDtos.RiskAssessmentDto risk = riskEngineService.getLatestOrCompute(user.getId());
        ForecastDtos.ForecastResultDto forecast = forecastService.generateForecast(user.getId(), horizon, today, false);
        PaymentDtos.PaymentPlanComparisonResponse paymentPlan = paymentPlannerService.comparePaymentSchedules(user.getId(), 60, Map.of());
        SimulationDtos.SimulationResponse scenario = req.scenarioId() != null
                ? simulationService.reopenSavedScenario(user.getId(), req.scenarioId())
                : null;

        byte[] pdfBytes = renderPdf(user, type, ledger, risk, forecast, paymentPlan, scenario);

        Instant now = Instant.now();
        String safeFile = "finsentinel-" + type.toLowerCase() + "-" + today + ".pdf";
        GeneratedReportEntity entity = new GeneratedReportEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setReportType(type);
        entity.setTitle("FinSentinel AI — " + formatTypeTitle(type) + " Report");
        entity.setFileName(safeFile);
        entity.setStatus("READY");
        entity.setPolicyVersion(risk.policyVersion() + " / " + forecast.modelVersion());
        entity.setFileBytes(pdfBytes);
        entity.setCreatedAt(now);
        reportRepository.save(entity);

        auditService.record(user.getId(), principal.role(), "PDF_REPORT_GENERATED", "GENERATED_REPORT", entity.getId().toString(), "SUCCESS", Map.of("type", type));
        return toSummary(entity);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryDto> listReports(UUID userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeneratedReportEntity getReportForDownload(UUID userId, UUID reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> ApiException.notFound("Report not found"));
    }

    private byte[] renderPdf(
            UserEntity user,
            String type,
            LedgerDtos.LedgerSummary ledger,
            RiskDtos.RiskAssessmentDto risk,
            ForecastDtos.ForecastResultDto forecast,
            PaymentDtos.PaymentPlanComparisonResponse paymentPlan,
            SimulationDtos.SimulationResponse scenario) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(20, 34, 29));
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(8, 127, 91));
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(20, 34, 29));
            Font muted = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(104, 119, 111));

            Paragraph title = new Paragraph("FinSentinel AI — " + formatTypeTitle(type) + " Report", h1);
            doc.add(title);
            doc.add(new Paragraph("Generated at (UTC): " + Instant.now()
                    + "  |  Account Holder: " + user.getFullName()
                    + (user.isDemoUser() ? " [DEMO PROFILE]" : ""), muted));
            doc.add(new Paragraph("Policy & Model Versions: " + risk.policyVersion() + " | " + forecast.modelVersion(), muted));
            doc.add(new Paragraph(" "));

            // Observed Financial Summary
            doc.add(new Paragraph("1. Observed Ledger Summary (Trailing 30 Days)", h2));
            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(6);
            addHeaderCell(summaryTable, "Available Balance");
            addHeaderCell(summaryTable, "Period Income");
            addHeaderCell(summaryTable, "Period Expenses");
            addHeaderCell(summaryTable, "Period Savings");
            summaryTable.addCell(cell(ledger.totalAvailableBalance().toPlainString(), body));
            summaryTable.addCell(cell(ledger.periodIncome().toPlainString(), body));
            summaryTable.addCell(cell(ledger.periodExpenses().toPlainString(), body));
            summaryTable.addCell(cell(ledger.periodSavings().toPlainString(), body));
            doc.add(summaryTable);
            doc.add(new Paragraph(" "));

            // Risk Assessment
            doc.add(new Paragraph("2. Explainable Risk Assessment (Observed & Illustrative)", h2));
            doc.add(new Paragraph("Vulnerability Score: " + risk.displayScore() + "/100 (" + risk.category()
                    + ")  |  Data Completeness: " + risk.dataCompleteness()
                    + (risk.insufficientData() ? "  |  INSUFFICIENT DATA" : ""), body));
            PdfPTable riskTable = new PdfPTable(4);
            riskTable.setWidthPercentage(100);
            riskTable.setSpacingBefore(6);
            addHeaderCell(riskTable, "Factor");
            addHeaderCell(riskTable, "Weight");
            addHeaderCell(riskTable, "Measured");
            addHeaderCell(riskTable, "Contribution");
            for (RiskDtos.RiskFactorDto f : risk.factors()) {
                riskTable.addCell(cell(f.factorLabel(), body));
                riskTable.addCell(cell(f.weight().movePointRight(2).stripTrailingZeros().toPlainString() + "%", body));
                riskTable.addCell(cell(f.measuredValue(), body));
                riskTable.addCell(cell(f.weightedContribution() != null ? f.weightedContribution() + " pts" : "N/A", body));
            }
            doc.add(riskTable);
            doc.add(new Paragraph("Disclaimer: " + risk.disclaimer(), muted));
            doc.add(new Paragraph(" "));

            // Projected Cash-Flow Forecast
            doc.add(new Paragraph("3. Projected Cash-Flow Outlook (" + forecast.horizonDays() + " Days — Projected, Not Observed)", h2));
            doc.add(new Paragraph("Opening Balance: " + forecast.openingBalance()
                    + " | Reserve Threshold: " + forecast.reserveThreshold()
                    + " | Min Projected Balance: " + forecast.minProjectedBalance()
                    + " (" + forecast.minBalanceDate() + ")", body));
            doc.add(new Paragraph("Shortage Date (<0): " + (forecast.shortageDate() != null ? forecast.shortageDate() : "None projected")
                    + " | Reserve Breach Date: " + (forecast.reserveBreachDate() != null ? forecast.reserveBreachDate() : "None projected"), body));
            doc.add(new Paragraph(forecast.recoveryMessage(), body));
            doc.add(new Paragraph(" "));

            // Scenario Comparison if included
            if (scenario != null) {
                doc.add(new Paragraph("4. Digital Twin / Stress Scenario: " + scenario.name() + " (Hypothetical)", h2));
                for (String line : scenario.impactSummary()) {
                    doc.add(new Paragraph("• " + line, body));
                }
                doc.add(new Paragraph(" "));
            }

            // Payment Schedules
            doc.add(new Paragraph((scenario != null ? "5" : "4") + ". Adaptive Payment Schedule Comparison (Planning Simulation)", h2));
            PdfPTable payTable = new PdfPTable(4);
            payTable.setWidthPercentage(100);
            payTable.setSpacingBefore(6);
            addHeaderCell(payTable, "Schedule Strategy");
            addHeaderCell(payTable, "Feasible");
            addHeaderCell(payTable, "Min Projected Bal");
            addHeaderCell(payTable, "Shortfall");
            for (PaymentDtos.ScenarioScheduleDto s : paymentPlan.scenarios()) {
                payTable.addCell(cell(s.scenarioName(), body));
                payTable.addCell(cell(s.feasible() ? "Yes" : "No (Shortfall)", body));
                payTable.addCell(cell(s.minProjectedBalance().toPlainString(), body));
                payTable.addCell(cell(s.shortfallAmount().toPlainString(), body));
            }
            doc.add(payTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Assumptions & Notes:", h2));
            for (String a : forecast.assumptions()) {
                doc.add(new Paragraph("- " + a, muted));
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF rendering failed", ex);
        }
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(new Color(8, 127, 91));
        c.setPadding(6);
        table.addCell(c);
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setPadding(5);
        return c;
    }

    private String formatTypeTitle(String type) {
        return switch (type) {
            case "RISK" -> "Explainable Risk Assessment";
            case "SCENARIO" -> "Scenario & Stress Test";
            case "PAYMENT" -> "Adaptive Payment Schedule";
            default -> "Financial Intelligence Summary";
        };
    }

    private ReportSummaryDto toSummary(GeneratedReportEntity e) {
        return new ReportSummaryDto(e.getId(), e.getReportType(), e.getTitle(), e.getFileName(), e.getStatus(), e.getPolicyVersion(), e.getCreatedAt());
    }
}
