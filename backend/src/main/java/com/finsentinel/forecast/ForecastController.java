package com.finsentinel.forecast;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public ForecastDtos.ForecastResultDto getForecast(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestParam(defaultValue = "90") int horizonDays,
            @RequestParam(required = false) LocalDate startDate) {
        return forecastService.generateForecast(principal.userId(), horizonDays, startDate, false);
    }

    @PostMapping("/snapshot")
    public ForecastDtos.ForecastResultDto createSnapshot(
            @AuthenticationPrincipal UserSessionPrincipal principal,
            @RequestParam(defaultValue = "90") int horizonDays) {
        return forecastService.generateForecast(principal.userId(), horizonDays, LocalDate.now(), true);
    }
}
