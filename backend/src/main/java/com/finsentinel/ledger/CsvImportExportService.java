package com.finsentinel.ledger;

import com.finsentinel.common.api.ApiException;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.UserSessionPrincipal;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class CsvImportExportService {

    private static final int MAX_ROWS = 2000;
    private static final long MAX_BYTES = 2 * 1024 * 1024L;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    public CsvImportExportService(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository,
                                  TransactionCategoryRepository categoryRepository,
                                  LedgerService ledgerService,
                                  AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    public record CsvPreviewRow(
            int rowNumber,
            LocalDate date,
            String txType,
            BigDecimal amount,
            String currency,
            String merchant,
            String description,
            boolean duplicate,
            String error
    ) {}

    public record CsvPreviewResponse(
            List<String> headers,
            int totalRows,
            int validRows,
            int duplicateRows,
            int errorRows,
            List<CsvPreviewRow> rows
    ) {}

    public record CsvConfirmRequest(
            UUID accountId,
            List<CsvPreviewRow> rows
    ) {}

    public record CsvImportResult(
            int importedCount,
            int skippedDuplicates,
            int skippedErrors
    ) {}

    public CsvPreviewResponse previewCsv(UUID userId, UUID accountId, MultipartFile file,
                                         String dateCol, String typeCol, String amountCol, String merchantCol, String descCol, String currencyCol) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_CSV", "Uploaded CSV file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("CSV_TOO_LARGE", "CSV file exceeds 2 MB import safety limit.");
        }
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ApiException.notFound("Target account not found."));

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            String dCol = resolveHeader(headers, dateCol, "date", "occurred_on", "transaction_date");
            String tCol = resolveHeader(headers, typeCol, "type", "tx_type", "kind");
            String aCol = resolveHeader(headers, amountCol, "amount", "value");
            String mCol = resolveHeader(headers, merchantCol, "merchant", "payee", "title");
            String descColumn = resolveHeader(headers, descCol, "description", "narration", "memo");
            String cCol = resolveHeader(headers, currencyCol, "currency");

            if (dCol == null || aCol == null) {
                throw ApiException.badRequest("MISSING_CSV_COLUMNS", "CSV must contain date and amount columns (or supply column mappings).");
            }

            List<CsvPreviewRow> rows = new ArrayList<>();
            int valid = 0, dups = 0, errors = 0;
            int rowIdx = 1;
            for (CSVRecord rec : parser) {
                if (rowIdx > MAX_ROWS) {
                    throw ApiException.badRequest("CSV_ROW_LIMIT", "CSV exceeds maximum allowed " + MAX_ROWS + " rows.");
                }
                String rawDate = rec.isMapped(dCol) ? rec.get(dCol) : "";
                String rawAmt = rec.isMapped(aCol) ? rec.get(aCol) : "";
                String rawType = tCol != null && rec.isMapped(tCol) ? rec.get(tCol) : "EXPENSE";
                String merchant = mCol != null && rec.isMapped(mCol) ? rec.get(mCol) : "";
                String desc = descColumn != null && rec.isMapped(descColumn) ? rec.get(descColumn) : "";
                String curr = cCol != null && rec.isMapped(cCol) && !rec.get(cCol).isBlank() ? rec.get(cCol).toUpperCase() : "INR";

                LocalDate parsedDate = null;
                BigDecimal parsedAmt = null;
                String normType = normalizeType(rawType);
                StringBuilder err = new StringBuilder();

                try {
                    parsedDate = LocalDate.parse(rawDate.trim());
                } catch (DateTimeParseException ex) {
                    err.append("Invalid ISO date '").append(rawDate).append("'; ");
                }

                try {
                    String cleaned = rawAmt.replaceAll("[,₹$\\s]", "");
                    parsedAmt = new BigDecimal(cleaned);
                    if (parsedAmt.compareTo(BigDecimal.ZERO) < 0) {
                        if (tCol == null) normType = "EXPENSE";
                        parsedAmt = parsedAmt.abs();
                    }
                    if (parsedAmt.compareTo(BigDecimal.ZERO) == 0) {
                        err.append("Amount must be greater than 0; ");
                    }
                } catch (Exception ex) {
                    err.append("Invalid numeric amount '").append(rawAmt).append("'; ");
                }

                if (!List.of("INR", "USD", "EUR", "GBP").contains(curr)) {
                    err.append("Unsupported currency '").append(curr).append("'; ");
                }

                boolean isDup = false;
                if (err.isEmpty() && parsedDate != null && parsedAmt != null) {
                    String hash = ledgerService.computeDedupHash(accountId, normType, parsedAmt, parsedDate, merchant);
                    isDup = transactionRepository.existsByUserIdAndDedupHash(userId, hash);
                    if (isDup) dups++;
                    else valid++;
                } else {
                    errors++;
                }

                rows.add(new CsvPreviewRow(rowIdx++, parsedDate, normType, parsedAmt, curr, merchant, desc, isDup, err.isEmpty() ? null : err.toString().trim()));
            }
            return new CsvPreviewResponse(headers, rows.size(), valid, dups, errors, rows);
        } catch (ApiException e) {
            throw e;
        } catch (Exception ex) {
            throw ApiException.badRequest("CSV_PARSE_ERROR", "Unable to parse CSV file: " + ex.getMessage());
        }
    }

    @Transactional
    public CsvImportResult confirmImport(UserSessionPrincipal principal, CsvConfirmRequest req) {
        AccountEntity account = accountRepository.findByIdAndUserId(req.accountId(), principal.userId())
                .orElseThrow(() -> ApiException.notFound("Target account not found."));
        int imported = 0, skippedDups = 0, skippedErr = 0;

        for (CsvPreviewRow row : req.rows()) {
            if (row.error() != null || row.date() == null || row.amount() == null || row.amount().compareTo(BigDecimal.ZERO) <= 0) {
                skippedErr++;
                continue;
            }
            String hash = ledgerService.computeDedupHash(account.getId(), row.txType(), row.amount(), row.date(), row.merchant());
            if (row.duplicate() || transactionRepository.existsByUserIdAndDedupHash(principal.userId(), hash)) {
                skippedDups++;
                continue;
            }
            TransactionEntity tx = new TransactionEntity();
            tx.setId(UUID.randomUUID());
            tx.setUserId(principal.userId());
            tx.setAccountId(account.getId());
            tx.setTxType(normalizeType(row.txType()));
            tx.setAmount(row.amount().setScale(4, RoundingMode.HALF_UP));
            tx.setCurrency(row.currency() != null ? row.currency() : account.getCurrency());
            tx.setOccurredOn(row.date());
            tx.setMerchant(row.merchant());
            tx.setDescription(row.description());
            tx.setEssential(false);
            tx.setRecurring(false);
            tx.setRecurrenceRule("NONE");
            tx.setDedupHash(hash);
            tx.setAnomalyStatus("NONE");
            tx.setCreatedAt(Instant.now());
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);
            imported++;
        }

        auditService.record(principal.userId(), principal.role(), "CSV_IMPORTED", "TRANSACTION", account.getId().toString(), "SUCCESS", Map.of("imported", imported, "skippedDups", skippedDups));
        return new CsvImportResult(imported, skippedDups, skippedErr);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID userId) {
        List<TransactionEntity> txs = transactionRepository.findByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
        Map<UUID, String> catNames = new HashMap<>();
        categoryRepository.findAvailableForUser(userId).forEach(c -> catNames.put(c.getId(), c.getName()));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("date", "type", "amount", "currency", "category", "merchant", "description", "essential")
                     .build())) {
            for (TransactionEntity tx : txs) {
                printer.printRecord(
                        tx.getOccurredOn().toString(),
                        tx.getTxType(),
                        tx.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        tx.getCurrency(),
                        sanitizeCsvCell(tx.getCategoryId() != null ? catNames.getOrDefault(tx.getCategoryId(), "") : ""),
                        sanitizeCsvCell(tx.getMerchant()),
                        sanitizeCsvCell(tx.getDescription()),
                        tx.isEssential()
                );
            }
            printer.flush();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
    }

    /**
     * Prevents spreadsheet CSV formula injection by prefixing values starting with =, +, -, @, tab, or CR.
     */
    public static String sanitizeCsvCell(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private String resolveHeader(List<String> headers, String explicit, String... candidates) {
        if (explicit != null && !explicit.isBlank()) {
            for (String h : headers) {
                if (h.equalsIgnoreCase(explicit.trim())) return h;
            }
        }
        for (String c : candidates) {
            for (String h : headers) {
                if (h.equalsIgnoreCase(c)) return h;
            }
        }
        return null;
    }

    private String normalizeType(String raw) {
        if (raw == null) return "EXPENSE";
        String u = raw.trim().toUpperCase();
        return switch (u) {
            case "CREDIT", "INCOME", "CR" -> "INCOME";
            case "TRANSFER" -> "TRANSFER";
            case "REFUND" -> "REFUND";
            case "SAVINGS" -> "SAVINGS";
            default -> "EXPENSE";
        };
    }
}
