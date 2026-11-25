package com.finsentinel.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineUnitTest {

    private final RiskPolicyStrategy policy = new StandardRiskPolicyV1();

    @Test
    void classificationThresholdsMatchDocumentedBoundaries() {
        assertThat(StandardRiskPolicyV1.classify(0)).isEqualTo("LOW");
        assertThat(StandardRiskPolicyV1.classify(30)).isEqualTo("LOW");
        assertThat(StandardRiskPolicyV1.classify(31)).isEqualTo("MODERATE");
        assertThat(StandardRiskPolicyV1.classify(60)).isEqualTo("MODERATE");
        assertThat(StandardRiskPolicyV1.classify(61)).isEqualTo("HIGH");
        assertThat(StandardRiskPolicyV1.classify(80)).isEqualTo("HIGH");
        assertThat(StandardRiskPolicyV1.classify(81)).isEqualTo("CRITICAL");
        assertThat(StandardRiskPolicyV1.classify(100)).isEqualTo("CRITICAL");
    }

    @Test
    void normalizedFactorsStayBoundedAndReproducible() {
        RiskDtos.RiskInputSnapshot snap = new RiskDtos.RiskInputSnapshot(
                LocalDate.of(2026, 11, 25),
                new BigDecimal("85000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("55000.00"),
                new BigDecimal("40000.00"),
                new BigDecimal("25000.00"),
                new BigDecimal("18000.00"),
                0,
                new BigDecimal("0.9200"),
                3,
                true,
                true
        );

        RiskPolicyStrategy.EvaluationResult r1 = policy.evaluate(snap);
        RiskPolicyStrategy.EvaluationResult r2 = policy.evaluate(snap);

        assertThat(r1.rawScore()).isEqualByComparingTo(r2.rawScore());
        assertThat(r1.displayScore()).isEqualTo(r2.displayScore());
        assertThat(r1.insufficientData()).isFalse();
        assertThat(r1.factors()).hasSize(6);
        for (RiskDtos.RiskFactorDto f : r1.factors()) {
            assertThat(f.dataAvailable()).isTrue();
            assertThat(f.normalizedValue()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        }
    }

    @Test
    void riskScoreIncreasesMonotonicallyWhenDebtAndDeficitWorsen() {
        RiskDtos.RiskInputSnapshot healthy = new RiskDtos.RiskInputSnapshot(
                LocalDate.of(2026, 11, 25),
                new BigDecimal("300000"),
                new BigDecimal("120000"),
                new BigDecimal("45000"),
                new BigDecimal("35000"),
                new BigDecimal("10000"),
                new BigDecimal("5000"),
                0,
                new BigDecimal("0.95"),
                4,
                true,
                true
        );

        RiskDtos.RiskInputSnapshot stressed = new RiskDtos.RiskInputSnapshot(
                LocalDate.of(2026, 11, 25),
                new BigDecimal("12000"),
                new BigDecimal("60000"),
                new BigDecimal("72000"),
                new BigDecimal("55000"),
                new BigDecimal("42000"),
                new BigDecimal("30000"),
                2,
                new BigDecimal("0.55"),
                4,
                true,
                true
        );

        RiskPolicyStrategy.EvaluationResult resHealthy = policy.evaluate(healthy);
        RiskPolicyStrategy.EvaluationResult resStressed = policy.evaluate(stressed);

        assertThat(resHealthy.displayScore()).isLessThan(resStressed.displayScore());
        assertThat(resHealthy.category()).isEqualTo("LOW");
        assertThat(resStressed.category()).isIn("HIGH", "CRITICAL");
    }

    @Test
    void missingInputsTriggerInsufficientDataWithoutSilentZeroing() {
        RiskDtos.RiskInputSnapshot empty = new RiskDtos.RiskInputSnapshot(
                LocalDate.of(2026, 11, 25),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                null,
                0,
                false,
                false
        );

        RiskPolicyStrategy.EvaluationResult res = policy.evaluate(empty);
        assertThat(res.insufficientData()).isTrue();
        assertThat(res.category()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(res.dataCompleteness()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.factors()).allMatch(f -> !f.dataAvailable() && f.normalizedValue() == null);
    }
}
