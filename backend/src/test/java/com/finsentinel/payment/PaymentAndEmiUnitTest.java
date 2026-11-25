package com.finsentinel.payment;

import com.finsentinel.common.api.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.PriorityQueue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAndEmiUnitTest {

    private final EmiCalculatorService emiService = new EmiCalculatorService();

    @Test
    void fixedRateAmortizingEmiReconcilesExactPrincipal() {
        PaymentDtos.EmiResultDto res = emiService.calculateEmi(new PaymentDtos.EmiRequest(
                new BigDecimal("100000.00"),
                new BigDecimal("12.00"),
                12,
                new BigDecimal("500.00"),
                "FIXED_AMORTIZING"
        ));

        assertThat(res.supported()).isTrue();
        assertThat(res.monthlyEmi()).isEqualByComparingTo("8884.88");
        assertThat(res.reconciledPrincipalSum()).isEqualByComparingTo("100000.00");
        assertThat(res.schedule()).hasSize(12);
        assertThat(res.schedule().get(11).closingPrincipal()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroInterestEmiDividesEvenlyAndReconcilesRemainder() {
        PaymentDtos.EmiResultDto res = emiService.calculateEmi(new PaymentDtos.EmiRequest(
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                3,
                BigDecimal.ZERO,
                "ZERO_INTEREST"
        ));

        assertThat(res.supported()).isTrue();
        assertThat(res.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(res.reconciledPrincipalSum()).isEqualByComparingTo("10000.00");
        assertThat(res.schedule()).extracting(PaymentDtos.AmortizationRow::principalComponent)
                .containsExactly(new BigDecimal("3333.33"), new BigDecimal("3333.33"), new BigDecimal("3333.34"));
    }

    @Test
    void variableRateModelExplicitlyMarkedUnsupported() {
        PaymentDtos.EmiResultDto res = emiService.calculateEmi(new PaymentDtos.EmiRequest(
                new BigDecimal("50000.00"),
                new BigDecimal("10.50"),
                12,
                BigDecimal.ZERO,
                "VARIABLE_RATE"
        ));
        assertThat(res.supported()).isFalse();
        assertThat(res.unsupportedReason()).contains("unsupported");
    }

    @Test
    void zeroInterestModelWithPositiveRateThrowsValidationError() {
        assertThatThrownBy(() -> emiService.calculateEmi(new PaymentDtos.EmiRequest(
                new BigDecimal("50000.00"),
                new BigDecimal("8.00"),
                12,
                BigDecimal.ZERO,
                "ZERO_INTEREST"
        ))).isInstanceOf(ApiException.class);
    }

    @Test
    void priorityQueueBreaksDueDateTiesByEssentialThenLateFee() {
        LocalDate sameDue = LocalDate.of(2026, 12, 5);
        FinancialObligationEntity nonEss = obligation("Streaming", sameDue, false, "0");
        FinancialObligationEntity essLowFee = obligation("Utility", sameDue, true, "100");
        FinancialObligationEntity essHighFee = obligation("Rent", sameDue, true, "500");

        PriorityQueue<FinancialObligationEntity> pq = new PriorityQueue<>(PaymentPlannerService.obligationComparator());
        pq.add(nonEss);
        pq.add(essLowFee);
        pq.add(essHighFee);

        assertThat(pq.poll().getTitle()).isEqualTo("Rent");
        assertThat(pq.poll().getTitle()).isEqualTo("Utility");
        assertThat(pq.poll().getTitle()).isEqualTo("Streaming");
    }

    private FinancialObligationEntity obligation(String title, LocalDate due, boolean essential, String fee) {
        FinancialObligationEntity ob = new FinancialObligationEntity();
        ob.setId(UUID.randomUUID());
        ob.setTitle(title);
        ob.setDueDate(due);
        ob.setEssential(essential);
        ob.setLateFee(new BigDecimal(fee));
        ob.setTotalAmount(new BigDecimal("1000"));
        return ob;
    }
}
