package com.finsentinel.simulation;

import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/scenarios/simulate")
    public SimulationDtos.SimulationResponse simulate(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                      @Valid @RequestBody SimulationDtos.SimulationParameters parameters) {
        return simulationService.simulate(principal.userId(), parameters, LocalDate.now());
    }

    @GetMapping("/scenarios")
    public List<SimulationDtos.SavedScenarioSummary> listScenarios(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return simulationService.listSavedScenarios(principal.userId());
    }

    @PostMapping("/scenarios")
    public ResponseEntity<SimulationDtos.SimulationResponse> saveScenario(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                          @Valid @RequestBody SimulationDtos.SimulationParameters parameters) {
        return ResponseEntity.status(HttpStatus.CREATED).body(simulationService.saveScenario(principal, parameters));
    }

    @GetMapping("/scenarios/{id}")
    public SimulationDtos.SimulationResponse reopenScenario(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                            @PathVariable UUID id) {
        return simulationService.reopenSavedScenario(principal.userId(), id);
    }

    @DeleteMapping("/scenarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        simulationService.deleteScenario(principal.userId(), id);
    }

    @PostMapping("/scenarios/compare")
    public List<SimulationDtos.SimulationResponse> compareScenarios(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                                    @Valid @RequestBody SimulationDtos.CompareRequest request) {
        return simulationService.compareScenarios(principal.userId(), request.cases());
    }

    @GetMapping("/stress-tests/templates")
    public List<SimulationDtos.StressTestTemplateDto> stressTemplates() {
        return simulationService.listStressTestTemplates();
    }

    @PostMapping("/stress-tests")
    public SimulationDtos.SimulationResponse runStressTest(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                           @Valid @RequestBody SimulationDtos.SimulationParameters parameters) {
        SimulationDtos.SimulationParameters tagged = new SimulationDtos.SimulationParameters(
                parameters.name(), "STRESS_TEST", parameters.templateKey(),
                parameters.horizonDays(), parameters.monthlyIncomeMultiplier(), parameters.incomeDelayDays(),
                parameters.essentialExpenseMultiplier(), parameters.discretionaryExpenseMultiplier(),
                parameters.savingsMultiplier(), parameters.unexpectedExpenseAmount(), parameters.unexpectedExpenseDate(),
                parameters.paymentDateShiftDays(), parameters.obligationDateOverrides(),
                parameters.reserveThresholdOverride(), parameters.clusterPaymentsTogether()
        );
        return simulationService.simulate(principal.userId(), tagged, LocalDate.now());
    }
}
