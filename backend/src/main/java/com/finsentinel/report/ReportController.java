package com.finsentinel.report;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final PdfReportService reportService;

    public ReportController(PdfReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<PdfReportService.ReportSummaryDto> list(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return reportService.listReports(principal.userId());
    }

    @PostMapping("/generate")
    public ResponseEntity<PdfReportService.ReportSummaryDto> generate(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestBody PdfReportService.GenerateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.generateReport(principal, request));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserSessionPrincipal principal,
                                           @PathVariable UUID id) {
        GeneratedReportEntity report = reportService.getReportForDownload(principal.userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(report.getFileBytes());
    }
}
