package com.finsentinel.payment;

import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentPlannerService plannerService;
    private final EmiCalculatorService emiService;

    public PaymentController(PaymentPlannerService plannerService, EmiCalculatorService emiService) {
        this.plannerService = plannerService;
        this.emiService = emiService;
    }

    @GetMapping("/payments")
    public List<PaymentDtos.ObligationDto> listPayments(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return plannerService.listObligations(principal.userId());
    }

    @GetMapping("/obligations")
    public List<PaymentDtos.ObligationDto> listObligations(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return plannerService.listObligations(principal.userId());
    }

    @PostMapping("/obligations")
    public ResponseEntity<PaymentDtos.ObligationDto> createObligation(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                      @Valid @RequestBody PaymentDtos.ObligationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createObligation(principal, request));
    }

    @PutMapping("/obligations/{id}")
    public PaymentDtos.ObligationDto updateObligation(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody PaymentDtos.ObligationRequest request) {
        return plannerService.updateObligation(principal, id, request);
    }

    @PatchMapping("/obligations/{id}/status")
    public PaymentDtos.ObligationDto updateStatus(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                  @PathVariable UUID id,
                                                  @RequestBody Map<String, String> body) {
        return plannerService.updateStatus(principal, id, body.get("status"));
    }

    @DeleteMapping("/obligations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObligation(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        plannerService.deleteObligation(principal, id);
    }

    @PostMapping("/payments/{obligationId}/record")
    public ResponseEntity<PaymentDtos.PaymentRecordDto> recordPayment(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                      @PathVariable UUID obligationId,
                                                                      @Valid @RequestBody PaymentDtos.RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.recordManualPayment(principal, obligationId, request));
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentDtos.ObligationDto> createPaymentObligationAlias(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                                  @Valid @RequestBody PaymentDtos.ObligationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plannerService.createObligation(principal, request));
    }

    @GetMapping("/payments/records")
    public List<PaymentDtos.PaymentRecordDto> listRecords(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return plannerService.listPaymentRecords(principal.userId());
    }

    @PostMapping("/payments/compare")
    public PaymentDtos.PaymentPlanComparisonResponse compareSchedules(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                      @RequestParam(defaultValue = "60") int horizonDays,
                                                                      @RequestBody(required = false) PaymentDtos.SimulateRescheduleRequest request) {
        Map<UUID, java.time.LocalDate> overrides = request != null && request.hypotheticalDates() != null ? request.hypotheticalDates() : Map.of();
        int h = request != null && request.horizonDays() != null ? request.horizonDays() : horizonDays;
        return plannerService.comparePaymentSchedules(principal.userId(), h, overrides);
    }

    @PostMapping("/payments/methods/compare")
    public PaymentDtos.MethodComparisonResponse compareMethods(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                               @Valid @RequestBody PaymentDtos.MethodComparisonInput input) {
        return plannerService.compareMethods(principal.userId(), input);
    }

    @PostMapping("/calculators/emi")
    public PaymentDtos.EmiResultDto calculateEmi(@Valid @RequestBody PaymentDtos.EmiRequest request) {
        return emiService.calculateEmi(request);
    }
}
