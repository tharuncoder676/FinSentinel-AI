package com.finsentinel.ledger;

import com.finsentinel.common.api.PagedResponse;
import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts")
    public List<LedgerDtos.AccountDto> listAccounts(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ledgerService.listAccounts(principal.userId());
    }

    @PostMapping("/accounts")
    public ResponseEntity<LedgerDtos.AccountDto> createAccount(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                               @Valid @RequestBody LedgerDtos.AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createAccount(principal, request));
    }

    @PutMapping("/accounts/{id}")
    public LedgerDtos.AccountDto updateAccount(@AuthenticationPrincipal UserSessionPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody LedgerDtos.AccountRequest request) {
        return ledgerService.updateAccount(principal, id, request);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        ledgerService.deleteAccount(principal, id);
    }

    @GetMapping("/categories")
    public List<LedgerDtos.CategoryDto> listCategories(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ledgerService.listCategories(principal.userId());
    }

    @PostMapping("/categories")
    public ResponseEntity<LedgerDtos.CategoryDto> createCategory(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                 @Valid @RequestBody LedgerDtos.CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createCategory(principal, request));
    }

    @GetMapping("/income")
    public List<LedgerDtos.IncomeSourceDto> listIncome(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ledgerService.listIncomeSources(principal.userId());
    }

    @PostMapping("/income")
    public ResponseEntity<LedgerDtos.IncomeSourceDto> createIncome(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                   @Valid @RequestBody LedgerDtos.IncomeSourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createIncomeSource(principal, request));
    }

    @PutMapping("/income/{id}")
    public LedgerDtos.IncomeSourceDto updateIncome(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody LedgerDtos.IncomeSourceRequest request) {
        return ledgerService.updateIncomeSource(principal, id, request);
    }

    @DeleteMapping("/income/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        ledgerService.deleteIncomeSource(principal, id);
    }

    @GetMapping("/transactions")
    public PagedResponse<LedgerDtos.TransactionDto> listTransactions(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String txType,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredOn") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ledgerService.listTransactions(principal.userId(), search, txType, categoryId, accountId, from, to, page, size, sortBy, sortDir);
    }

    @PostMapping("/transactions")
    public ResponseEntity<LedgerDtos.TransactionDto> createTransaction(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                       @Valid @RequestBody LedgerDtos.TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createTransaction(principal, request));
    }

    @PutMapping("/transactions/{id}")
    public LedgerDtos.TransactionDto updateTransaction(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody LedgerDtos.TransactionRequest request) {
        return ledgerService.updateTransaction(principal, id, request);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        ledgerService.deleteTransaction(principal, id);
    }

    @GetMapping("/expenses")
    public List<LedgerDtos.ExpenseDto> listExpenses(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ledgerService.listExpenses(principal.userId());
    }

    @PostMapping("/expenses")
    public ResponseEntity<LedgerDtos.ExpenseDto> createExpense(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                               @Valid @RequestBody LedgerDtos.ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createExpense(principal, request));
    }

    @PutMapping("/expenses/{id}")
    public LedgerDtos.ExpenseDto updateExpense(@AuthenticationPrincipal UserSessionPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody LedgerDtos.ExpenseRequest request) {
        return ledgerService.updateExpense(principal, id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        ledgerService.deleteExpense(principal, id);
    }

    @GetMapping("/goals")
    public List<LedgerDtos.SavingsGoalDto> listGoals(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ledgerService.listGoals(principal.userId());
    }

    @PostMapping("/goals")
    public ResponseEntity<LedgerDtos.SavingsGoalDto> createGoal(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                               @Valid @RequestBody LedgerDtos.SavingsGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createGoal(principal, request));
    }

    @PutMapping("/goals/{id}")
    public LedgerDtos.SavingsGoalDto updateGoal(@AuthenticationPrincipal UserSessionPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody LedgerDtos.SavingsGoalRequest request) {
        return ledgerService.updateGoal(principal, id, request);
    }

    @PostMapping("/goals/{id}/contribute")
    public LedgerDtos.SavingsGoalDto contributeGoal(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                     @PathVariable UUID id,
                                                     @RequestBody Map<String, BigDecimal> body) {
        return ledgerService.contributeToGoal(principal, id, body.get("amount"));
    }

    @DeleteMapping("/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        ledgerService.deleteGoal(principal, id);
    }
}
