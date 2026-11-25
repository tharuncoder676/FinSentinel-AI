package com.finsentinel.forecast;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastEngineUnitTest {

    private final DeterministicEventForecastStrategy strategy = new DeterministicEventForecastStrategy();

    @Test
    void sameDayOrderingAppliesIncomeBeforeObligations() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        UUID obId = UUID.randomUUID();
        List<ForecastDtos.CashFlowEvent> events = List.of(
                new ForecastDtos.CashFlowEvent(start, ForecastDtos.EventKind.OBLIGATION_ESSENTIAL, "Rent", new BigDecimal("-20000"), true, false, obId),
                new ForecastDtos.CashFlowEvent(start, ForecastDtos.EventKind.INCOME, "Salary", new BigDecimal("50000"), true, false, UUID.randomUUID())
        );

        ForecastDtos.ForecastResultDto res = strategy.project(
                start, 7, new BigDecimal("5000"), new BigDecimal("10000"), events, false, List.of());

        // Day 0: 5000 + 50000 - 20000 = 35000; never goes negative because income orders before obligation
        assertThat(res.dailyPoints().get(0).closingBalance()).isEqualByComparingTo("35000.00");
        assertThat(res.shortageDate()).isNull();
        assertThat(res.affectedObligations()).isEmpty();
    }

    @Test
    void distinguishesReserveBreachFromCashShortfallAndFindsRecovery() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        UUID ob1 = UUID.randomUUID();
        UUID ob2 = UUID.randomUUID();
        List<ForecastDtos.CashFlowEvent> events = List.of(
                new ForecastDtos.CashFlowEvent(start.plusDays(2), ForecastDtos.EventKind.OBLIGATION_ESSENTIAL, "EMI", new BigDecimal("-15000"), true, false, ob1),
                new ForecastDtos.CashFlowEvent(start.plusDays(5), ForecastDtos.EventKind.OBLIGATION_ESSENTIAL, "Medical", new BigDecimal("-12000"), true, false, ob2),
                new ForecastDtos.CashFlowEvent(start.plusDays(10), ForecastDtos.EventKind.INCOME, "Salary", new BigDecimal("45000"), true, false, UUID.randomUUID())
        );

        ForecastDtos.ForecastResultDto res = strategy.project(
                start, 20, new BigDecimal("20000"), new BigDecimal("10000"), events, false, List.of());

        // Day 2: 20000 - 15000 = 5000 -> reserve breach (<10000), not negative
        assertThat(res.reserveBreachDate()).isEqualTo(start.plusDays(2));
        // Day 5: 5000 - 12000 = -7000 -> shortfall (<0)
        assertThat(res.shortageDate()).isEqualTo(start.plusDays(5));
        assertThat(res.minProjectedBalance()).isEqualByComparingTo("-7000.00");
        // Day 10: -7000 + 45000 = 38000 >= 10000 -> recovery
        assertThat(res.recoveryDate()).isEqualTo(start.plusDays(10));
    }

    @Test
    void monthEndRecurrenceClampsCorrectlyAcrossLeapAndNonLeapFebruary() {
        List<LocalDate> nonLeap = ForecastService.expandRecurrence(
                LocalDate.of(2025, 1, 31), "MONTHLY",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 30), 31);
        assertThat(nonLeap).containsExactly(
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 4, 30)
        );

        List<LocalDate> leap = ForecastService.expandRecurrence(
                LocalDate.of(2024, 1, 31), "MONTHLY",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), 31);
        assertThat(leap).containsExactly(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 31)
        );
    }
}
