package com.finsentinel.forecast;

import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.ledger.TransactionEntity;
import com.finsentinel.ledger.TransactionRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {

    private final TransactionRepository transactionRepository;
    private final ForecastService forecastService;

    public TimelineController(TransactionRepository transactionRepository, ForecastService forecastService) {
        this.transactionRepository = transactionRepository;
        this.forecastService = forecastService;
    }

    public record TimelineEventItem(
            String id,
            LocalDate date,
            String eventType,
            String title,
            BigDecimal signedAmount,
            boolean actual,
            boolean hypothetical,
            boolean reserveBreach,
            boolean shortfall,
            BigDecimal projectedBalanceAfter,
            String categoryOrStatus
    ) {}

    public record TimelineResponse(
            LocalDate from,
            LocalDate to,
            List<TimelineEventItem> historicalEvents,
            List<TimelineEventItem> projectedEvents
    ) {}

    @GetMapping
    public TimelineResponse getTimeline(@AuthenticationPrincipal UserSessionPrincipal principal,
                                        @RequestParam(defaultValue = "30") int historyDays,
                                        @RequestParam(defaultValue = "90") int forecastDays) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(Math.max(7, Math.min(365, historyDays)));
        int safeForecast = Math.max(7, Math.min(365, forecastDays));

        List<TransactionEntity> txs = transactionRepository.findByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(
                principal.userId(), from, today);

        List<TimelineEventItem> historical = new ArrayList<>();
        for (TransactionEntity tx : txs) {
            BigDecimal signed = switch (tx.getTxType()) {
                case "INCOME", "REFUND" -> tx.getAmount();
                case "TRANSFER" -> BigDecimal.ZERO;
                default -> tx.getAmount().negate();
            };
            historical.add(new TimelineEventItem(
                    tx.getId().toString(),
                    tx.getOccurredOn(),
                    tx.getTxType(),
                    tx.getMerchant() != null ? tx.getMerchant() : (tx.getDescription() != null ? tx.getDescription() : tx.getTxType()),
                    signed,
                    true,
                    false,
                    false,
                    false,
                    null,
                    tx.isEssential() ? "Essential" : "Recorded"
            ));
        }

        ForecastDtos.ForecastResultDto forecast = forecastService.generateForecast(principal.userId(), safeForecast, today, false);
        List<TimelineEventItem> projected = new ArrayList<>();
        for (ForecastDtos.DailyProjectionPoint pt : forecast.dailyPoints()) {
            for (ForecastDtos.CashFlowEvent ev : pt.events()) {
                projected.add(new TimelineEventItem(
                        UUID.randomUUID().toString(),
                        pt.date(),
                        ev.kind().name(),
                        ev.title(),
                        ev.signedAmount(),
                        false,
                        false,
                        pt.reserveBreach(),
                        pt.shortfall(),
                        pt.closingBalance(),
                        ev.overdue() ? "Overdue" : "Projected"
                ));
            }
            if (pt.events().isEmpty() && (pt.shortfall() || pt.reserveBreach())) {
                projected.add(new TimelineEventItem(
                        "breach-" + pt.date(),
                        pt.date(),
                        pt.shortfall() ? "SHORTFALL" : "RESERVE_BREACH",
                        pt.shortfall() ? "Projected Cash Shortfall" : "Projected Reserve Threshold Breach",
                        BigDecimal.ZERO,
                        false,
                        false,
                        pt.reserveBreach(),
                        pt.shortfall(),
                        pt.closingBalance(),
                        pt.shortfall() ? "Critical" : "Warning"
                ));
            }
        }
        projected.sort(Comparator.comparing(TimelineEventItem::date));
        return new TimelineResponse(from, today.plusDays(safeForecast), historical, projected);
    }
}
