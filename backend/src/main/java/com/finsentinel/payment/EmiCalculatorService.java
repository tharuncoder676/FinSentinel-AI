package com.finsentinel.payment;

import com.finsentinel.common.api.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmiCalculatorService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    public PaymentDtos.EmiResultDto calculateEmi(PaymentDtos.EmiRequest req) {
        String model = req.rateModel() != null ? req.rateModel()
                : (req.annualInterestRatePercent().compareTo(BigDecimal.ZERO) == 0 ? "ZERO_INTEREST" : "FIXED_AMORTIZING");

        if ("VARIABLE_RATE".equals(model)) {
            return new PaymentDtos.EmiResultDto(
                    false,
                    "Variable-rate or floating-rate fee models are unsupported in this deterministic planner. Use FIXED_AMORTIZING or ZERO_INTEREST.",
                    model,
                    req.principal(),
                    req.annualInterestRatePercent(),
                    req.tenureMonths(),
                    req.processingFee() != null ? req.processingFee() : BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of("Variable rate projections are explicitly marked unsupported to avoid unverified interest assumptions."),
                    List.of()
            );
        }

        BigDecimal principal = req.principal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = req.processingFee() != null ? req.processingFee().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        int n = req.tenureMonths();
        BigDecimal annualRate = req.annualInterestRatePercent();

        if (n <= 0 || principal.compareTo(BigDecimal.ZERO) <= 0 || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("INVALID_EMI_INPUT", "Principal and tenure must be positive and interest rate non-negative.");
        }

        List<PaymentDtos.AmortizationRow> schedule = new ArrayList<>(n);
        BigDecimal monthlyEmi;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal reconciledPrincipal = BigDecimal.ZERO;

        if (annualRate.compareTo(BigDecimal.ZERO) == 0 || "ZERO_INTEREST".equals(model)) {
            if (annualRate.compareTo(BigDecimal.ZERO) > 0 && "ZERO_INTEREST".equals(model)) {
                throw ApiException.badRequest("INVALID_EMI_MODEL", "ZERO_INTEREST model requires 0% annual interest rate.");
            }
            BigDecimal baseInstallment = principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            BigDecimal remaining = principal;
            for (int i = 1; i <= n; i++) {
                BigDecimal principalPart = (i == n) ? remaining : baseInstallment.min(remaining);
                BigDecimal closing = remaining.subtract(principalPart).setScale(2, RoundingMode.HALF_UP);
                schedule.add(new PaymentDtos.AmortizationRow(i, remaining, principalPart, principalPart, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), closing));
                reconciledPrincipal = reconciledPrincipal.add(principalPart);
                remaining = closing;
            }
            monthlyEmi = baseInstallment;
        } else {
            BigDecimal r = annualRate.divide(TWELVE_HUNDRED, MC);
            BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
            BigDecimal pow = onePlusR.pow(n, MC);
            BigDecimal numerator = principal.multiply(r, MC).multiply(pow, MC);
            BigDecimal denominator = pow.subtract(BigDecimal.ONE, MC);
            monthlyEmi = numerator.divide(denominator, 2, RoundingMode.HALF_UP);

            BigDecimal remaining = principal;
            for (int i = 1; i <= n; i++) {
                BigDecimal interest = remaining.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
                BigDecimal principalPart;
                BigDecimal installmentAmount;
                if (i == n) {
                    // Final installment reconciles exact remaining principal so total principal matches original principal
                    principalPart = remaining;
                    installmentAmount = principalPart.add(interest).setScale(2, RoundingMode.HALF_UP);
                } else {
                    principalPart = monthlyEmi.subtract(interest).setScale(2, RoundingMode.HALF_UP);
                    if (principalPart.compareTo(remaining) > 0) {
                        principalPart = remaining;
                    }
                    installmentAmount = principalPart.add(interest).setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal closing = remaining.subtract(principalPart).setScale(2, RoundingMode.HALF_UP);
                schedule.add(new PaymentDtos.AmortizationRow(i, remaining, installmentAmount, principalPart, interest, closing));
                totalInterest = totalInterest.add(interest);
                reconciledPrincipal = reconciledPrincipal.add(principalPart);
                remaining = closing;
            }
        }

        BigDecimal totalPayable = reconciledPrincipal.add(totalInterest).add(fee).setScale(2, RoundingMode.HALF_UP);

        return new PaymentDtos.EmiResultDto(
                true,
                null,
                annualRate.compareTo(BigDecimal.ZERO) == 0 ? "ZERO_INTEREST" : "FIXED_AMORTIZING",
                principal,
                annualRate.setScale(2, RoundingMode.HALF_UP),
                n,
                fee,
                monthlyEmi,
                totalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPayable,
                reconciledPrincipal.setScale(2, RoundingMode.HALF_UP),
                List.of(
                        "Standard fixed-rate amortizing formula P*r*(1+r)^n / ((1+r)^n - 1) using BigDecimal HALF_UP rounding.",
                        "Zero-interest branch divides principal equally with exact final installment reconciliation.",
                        "Educational simulation only; no real loan offer or credit check."
                ),
                schedule
        );
    }

    public PaymentDtos.MethodComparisonResponse comparePaymentMethods(
            PaymentDtos.MethodComparisonInput input,
            BigDecimal availableBalance,
            BigDecimal reserveThreshold) {

        BigDecimal amount = input.purchaseAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal upiFee = nz(input.upiFee());
        BigDecimal debitFee = nz(input.debitCardFee());
        BigDecimal creditFee = nz(input.creditCardFee());
        BigDecimal netBankFee = nz(input.netBankingFee());
        BigDecimal emiFee = nz(input.emiProcessingFee());

        BigDecimal carryRate = nz(input.creditCardMonthlyCarryRatePercent());
        int carryMonths = input.creditCardMonthsToPay() != null ? input.creditCardMonthsToPay() : 1;
        BigDecimal creditCarryInterest = carryMonths > 1
                ? amount.multiply(carryRate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(carryMonths - 1)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        int emiTenure = input.emiTenureMonths() != null ? input.emiTenureMonths() : 6;
        BigDecimal emiRate = nz(input.emiAnnualInterestPercent());
        PaymentDtos.EmiResultDto emiResult = calculateEmi(new PaymentDtos.EmiRequest(
                amount, emiRate, emiTenure, emiFee,
                emiRate.compareTo(BigDecimal.ZERO) == 0 ? "ZERO_INTEREST" : "FIXED_AMORTIZING"
        ));

        List<PaymentDtos.PaymentMethodOptionDto> options = new ArrayList<>();
        options.add(buildImmediateOption("UPI", "UPI Instant Transfer", amount, upiFee, availableBalance, reserveThreshold,
                "Assumes user-supplied UPI fee of " + upiFee + ". Immediate deduction from liquid balance."));
        options.add(buildImmediateOption("DEBIT_CARD", "Debit Card", amount, debitFee, availableBalance, reserveThreshold,
                "Assumes user-supplied card fee of " + debitFee + ". Immediate deduction from bank balance."));
        options.add(buildImmediateOption("NET_BANKING", "Net Banking", amount, netBankFee, availableBalance, reserveThreshold,
                "Assumes user-supplied convenience fee of " + netBankFee + ". Immediate bank outflow."));

        BigDecimal creditTotalCost = amount.add(creditFee).add(creditCarryInterest).setScale(2, RoundingMode.HALF_UP);
        options.add(new PaymentDtos.PaymentMethodOptionDto(
                "CREDIT_CARD",
                "Credit Card (" + carryMonths + " mo settlement)",
                creditFee.add(creditCarryInterest).setScale(2, RoundingMode.HALF_UP),
                creditTotalCost,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                creditTotalCost.divide(BigDecimal.valueOf(carryMonths), 2, RoundingMode.HALF_UP),
                carryMonths == 1 ? "Defers outflow to billing cycle; zero carry if paid in full." : "Revolving carry cost adds " + creditCarryInterest + ".",
                "Uses user-supplied fee (" + creditFee + ") and monthly carry rate (" + carryRate + "%)."
        ));

        options.add(new PaymentDtos.PaymentMethodOptionDto(
                "EMI",
                "Installment EMI (" + emiTenure + " months @ " + emiRate + "% p.a.)",
                emiResult.totalInterest().add(emiFee).setScale(2, RoundingMode.HALF_UP),
                emiResult.totalPayableWithFee(),
                emiFee,
                emiResult.monthlyEmi(),
                availableBalance.subtract(emiResult.monthlyEmi()).compareTo(reserveThreshold) >= 0
                        ? "Preserves short-term reserve; adds " + emiResult.monthlyEmi() + "/mo fixed commitment."
                        : "Monthly EMI strains projected reserve threshold.",
                "Fixed amortizing schedule with user-supplied rate and processing fee."
        ));

        return new PaymentDtos.MethodComparisonResponse(
                amount,
                availableBalance.setScale(2, RoundingMode.HALF_UP),
                reserveThreshold.setScale(2, RoundingMode.HALF_UP),
                "Educational payment method comparison using user-supplied rates and fees only. No lending offer or bank integration.",
                options,
                emiResult
        );
    }

    private PaymentDtos.PaymentMethodOptionDto buildImmediateOption(
            String method, String label, BigDecimal principal, BigDecimal fee,
            BigDecimal availableBalance, BigDecimal reserveThreshold, String assumptions) {
        BigDecimal total = principal.add(fee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal after = availableBalance.subtract(total);
        String impact;
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            impact = "Causes immediate cash shortfall of " + after.abs().setScale(2, RoundingMode.HALF_UP) + ".";
        } else if (after.compareTo(reserveThreshold) < 0) {
            impact = "Breaches reserve threshold (balance drops to " + after.setScale(2, RoundingMode.HALF_UP) + ").";
        } else {
            impact = "Affordable immediately while maintaining reserve threshold.";
        }
        return new PaymentDtos.PaymentMethodOptionDto(
                method, label, fee, total, total, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), impact, assumptions);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
