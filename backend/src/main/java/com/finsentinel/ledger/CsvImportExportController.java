package com.finsentinel.ledger;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class CsvImportExportController {

    private final CsvImportExportService csvService;

    public CsvImportExportController(CsvImportExportService csvService) {
        this.csvService = csvService;
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportExportService.CsvPreviewResponse preview(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestParam UUID accountId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String dateCol,
            @RequestParam(required = false) String typeCol,
            @RequestParam(required = false) String amountCol,
            @RequestParam(required = false) String merchantCol,
            @RequestParam(required = false) String descCol,
            @RequestParam(required = false) String currencyCol) {
        return csvService.previewCsv(principal.userId(), accountId, file, dateCol, typeCol, amountCol, merchantCol, descCol, currencyCol);
    }

    @PostMapping("/import")
    public CsvImportExportService.CsvImportResult confirmImport(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestBody CsvImportExportService.CsvConfirmRequest request) {
        return csvService.confirmImport(principal, request);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal UserSessionPrincipal principal) {
        byte[] csv = csvService.exportCsv(principal.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finsentinel-transactions.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
