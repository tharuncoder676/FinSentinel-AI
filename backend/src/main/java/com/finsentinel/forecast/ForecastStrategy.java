package com.finsentinel.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ForecastStrategy {
    String modelVersion();

    ForecastDtos.ForecastResultDto project(
            LocalDate startDate,
            int horizonDays,
            BigDecimal openingBalance,
            BigDecimal reserveThreshold,
            List<ForecastDtos.CashFlowEvent> scheduledEvents,
            boolean insufficientData,
            List<String> extraAssumptions
    );
}
