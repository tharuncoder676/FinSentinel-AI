package com.finsentinel.forecast;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Component
public class DeterministicEventForecastStrategy implements ForecastStrategy {

    public static final String MODEL_VERSION = "cashflow-deterministic-v1.0";

    private static final Map<ForecastDtos.EventKind, Integer> SAME_DAY_ORDER = Map.of(
            ForecastDtos.EventKind.INCOME, 1,
            ForecastDtos.EventKind.SAVINGS, 2,
            ForecastDtos.EventKind.OBLIGATION_ESSENTIAL, 3,
            ForecastDtos.EventKind.EXPENSE_ESSENTIAL, 4,
            ForecastDtos.EventKind.OBLIGATION_DISCRETIONARY, 5,
            ForecastDtos.EventKind.EXPENSE_DISCRETIONARY, 6
    );

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public ForecastDtos.ForecastResultDto project(
            LocalDate startDate,
            int horizonDays,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            List<ForecastDtos.CashFlowEvent> scheduledEvents,
            boolean insufficientData,
            List<String> extraAssumptions) {

        int safeHorizon = Math.max(1, Math.min(365, horizonDays));
        BigDecimal running = openingBalance.setScale(2, RoundingMode.HALF_UP);
        BigDecimal threshold = reserveThreshold.setScale(2, RoundingMode.HALF_UP);
        LocalDate endDate = startDate.plusDays(safeHorizon);

        Map<LocalDate, List<ForecastDtos.CashFlowEvent>> eventsByDate = new HashMap<>();
        for (ForecastDtos.CashFlowEvent ev : scheduledEvents) {
            LocalDate effectiveDate = ev.date().isBefore(startDate) ? startDate : ev.date();
            if (!effectiveDate.isAfter(endDate)) {
                eventsByDate.computeIfAbsent(effectiveDate, d -> new ArrayList<>()).add(ev);
            }
        }

        Comparator<ForecastDtos.CashFlowEvent> sameDayComparator = Comparator
                .comparingInt((ForecastDtos.CashFlowEvent e) -> SAME_DAY_ORDER.getOrDefault(e.kind(), 9))
                .thenComparing(ForecastDtos.CashFlowEvent::title, Comparator.nullsLast(String::compareTo));

        List<ForecastDtos.DailyProjectionPoint> points = new ArrayList<>(safeHorizon + 1);
        List<ForecastDtos.AffectedObligationDto> affectedObligations = new ArrayList<>();

        BigDecimal minBalance = running;
        LocalDate minBalanceDate = startDate;
        LocalDate shortageDate = null;
        LocalDate reserveBreachDate = null;
        LocalDate recoveryDate = null;

        for (int offset = 0; offset <= safeHorizon; offset++) {
            LocalDate day = startDate.plusDays(offset);
            BigDecimal dayOpening = running;
            List<ForecastDtos.CashFlowEvent> dayEvents = eventsByDate.getOrDefault(day, List.of());
            List<ForecastDtos.CashFlowEvent> sorted = dayEvents.stream().sorted(sameDayComparator).toList();

            BigDecimal credits = BigDecimal.ZERO;
            BigDecimal debits = BigDecimal.ZERO;

            for (ForecastDtos.CashFlowEvent ev : sorted) {
                BigDecimal amt = ev.signedAmount().setScale(2, RoundingMode.HALF_UP);
                running = running.add(amt);
                if (amt.compareTo(BigDecimal.ZERO) >= 0) {
                    credits = credits.add(amt);
                } else {
                    debits = debits.add(amt.abs());
                }

                if ((ev.kind() == ForecastDtos.EventKind.OBLIGATION_ESSENTIAL
                        || ev.kind() == ForecastDtos.EventKind.OBLIGATION_DISCRETIONARY)
                        && ev.sourceId() != null) {
                    boolean causesShortfall = running.compareTo(BigDecimal.ZERO) < 0;
                    boolean breachesReserve = running.compareTo(threshold) < 0;
                    if (causesShortfall || breachesReserve) {
                        affectedObligations.add(new ForecastDtos.AffectedObligationDto(
                                ev.sourceId(),
                                ev.title(),
                                ev.date(),
                                amt.abs(),
                                running,
                                causesShortfall,
                                breachesReserve
                        ));
                    }
                }
            }

            boolean shortfall = running.compareTo(BigDecimal.ZERO) < 0;
            boolean reserveBreach = running.compareTo(threshold) < 0;

            if (running.compareTo(minBalance) < 0) {
                minBalance = running;
                minBalanceDate = day;
            }
            if (shortfall && shortageDate == null) {
                shortageDate = day;
            }
            if (reserveBreach && reserveBreachDate == null) {
                reserveBreachDate = day;
            }
            if ((shortageDate != null || reserveBreachDate != null)
                    && recoveryDate == null
                    && day.isAfter(minBalanceDate)
                    && running.compareTo(threshold) >= 0) {
                recoveryDate = day;
            }

            points.add(new ForecastDtos.DailyProjectionPoint(
                    day,
                    dayOpening,
                    credits.setScale(2, RoundingMode.HALF_UP),
                    debits.setScale(2, RoundingMode.HALF_UP),
                    running.setScale(2, RoundingMode.HALF_UP),
                    shortfall,
                    reserveBreach,
                    sorted
            ));
        }

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Same-day deterministic ordering: (1) Income credits, (2) Savings transfers, (3) Essential obligations, (4) Essential expenses, (5) Discretionary obligations, (6) Discretionary expenses.");
        assumptions.add("Projected shortfall defined as closing balance < 0; reserve breach defined separately as closing balance < " + threshold + ".");
        assumptions.add("Monetary arithmetic uses BigDecimal with HALF_UP rounding to 2 decimal places.");
        if (extraAssumptions != null) {
            assumptions.addAll(extraAssumptions);
        }

        String recoveryMessage;
        if (shortageDate == null && reserveBreachDate == null) {
            recoveryMessage = "No shortfall or reserve breach projected within the " + safeHorizon + "-day horizon.";
        } else if (recoveryDate != null) {
            recoveryMessage = "Projected balance recovers above reserve threshold on " + recoveryDate + ".";
        } else {
            recoveryMessage = "No recovery above reserve threshold observed within the " + safeHorizon + "-day horizon.";
        }

        return new ForecastDtos.ForecastResultDto(
                MODEL_VERSION,
                startDate,
                safeHorizon,
                openingBalance.setScale(2, RoundingMode.HALF_UP),
                threshold,
                minBalance.setScale(2, RoundingMode.HALF_UP),
                minBalanceDate,
                shortageDate,
                reserveBreachDate,
                recoveryDate,
                recoveryMessage,
                insufficientData,
                assumptions,
                points,
                affectedObligations,
                Instant.now()
        );
    }
}
