package com.calcforge.service;

import com.calcforge.dto.request.CompoundInterestRequest;
import com.calcforge.dto.request.LoanRequest;
import com.calcforge.dto.request.NpvRequest;
import com.calcforge.dto.request.TipSplitRequest;
import com.calcforge.dto.response.CompoundInterestResponse;
import com.calcforge.dto.response.LoanResponse;
import com.calcforge.dto.response.NpvResponse;
import com.calcforge.dto.response.TipSplitResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Everyday finance calculators. All money figures are computed at high internal
 * precision and rounded to 2 decimal places only at the final, displayed result -
 * rounding intermediate amortization rows would compound small errors over long schedules.
 */
@Service
public class FinanceService {

    private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);
    private static final int MAX_TERM_MONTHS = 1200; // 100 years
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONEY_ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public LoanResponse amortize(LoanRequest request) {
        if (request.termMonths() > MAX_TERM_MONTHS) {
            throw new IllegalArgumentException("termMonths cannot exceed " + MAX_TERM_MONTHS);
        }
        BigDecimal principal = request.principal();
        BigDecimal monthlyRate = request.annualInterestRatePercent().divide(HUNDRED, MC).divide(BigDecimal.valueOf(12), MC);
        int n = request.termMonths();

        BigDecimal payment;
        if (monthlyRate.signum() == 0) {
            payment = principal.divide(BigDecimal.valueOf(n), MC);
        } else {
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate, MC);
            BigDecimal onePlusRPowN = onePlusR.pow(n, MC);
            BigDecimal denominator = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(onePlusRPowN, MC), MC);
            payment = principal.multiply(monthlyRate, MC).divide(denominator, MC);
        }
        BigDecimal paymentRounded = payment.setScale(2, RoundingMode.HALF_UP);

        List<LoanResponse.AmortizationRowDto> schedule = new ArrayList<>();
        BigDecimal balance = principal;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        boolean includeSchedule = request.includeSchedule() == null || request.includeSchedule();

        for (int period = 1; period <= n; period++) {
            BigDecimal interestPortion = balance.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualPayment = (period == n)
                    ? balance.add(interestPortion, MC).setScale(2, RoundingMode.HALF_UP) // absorb rounding drift on last payment
                    : paymentRounded;
            BigDecimal principalPortion = actualPayment.subtract(interestPortion).setScale(2, RoundingMode.HALF_UP);
            balance = balance.subtract(principalPortion, MC).setScale(2, RoundingMode.HALF_UP);
            if (balance.signum() < 0) {
                balance = MONEY_ZERO;
            }

            totalPaid = totalPaid.add(actualPayment);
            totalInterest = totalInterest.add(interestPortion);

            if (includeSchedule) {
                schedule.add(new LoanResponse.AmortizationRowDto(period, actualPayment, principalPortion, interestPortion, balance));
            }
        }

        return new LoanResponse(paymentRounded, totalPaid.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP), schedule);
    }

    public CompoundInterestResponse compoundInterest(CompoundInterestRequest request) {
        BigDecimal periodicRate = request.annualInterestRatePercent().divide(HUNDRED, MC)
                .divide(BigDecimal.valueOf(request.compoundsPerYear()), MC);
        BigDecimal periodsDecimal = request.years().multiply(BigDecimal.valueOf(request.compoundsPerYear()), MC);
        int periods = periodsDecimal.setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (periods > MAX_TERM_MONTHS * 5) {
            throw new IllegalArgumentException("Requested number of compounding periods is too large");
        }
        BigDecimal contribution = request.contributionPerPeriod() == null ? BigDecimal.ZERO : request.contributionPerPeriod();

        BigDecimal onePlusI = BigDecimal.ONE.add(periodicRate, MC);
        BigDecimal growthFactor = onePlusI.pow(Math.max(periods, 0), MC);

        BigDecimal principalGrown = request.principal().multiply(growthFactor, MC);
        BigDecimal contributionsGrown;
        if (periodicRate.signum() == 0) {
            contributionsGrown = contribution.multiply(BigDecimal.valueOf(periods), MC);
        } else {
            contributionsGrown = contribution.multiply(growthFactor.subtract(BigDecimal.ONE, MC), MC).divide(periodicRate, MC);
        }

        BigDecimal futureValue = principalGrown.add(contributionsGrown, MC);
        BigDecimal totalContributed = request.principal().add(contribution.multiply(BigDecimal.valueOf(periods), MC), MC);
        BigDecimal interestEarned = futureValue.subtract(totalContributed, MC);

        return new CompoundInterestResponse(
                futureValue.setScale(2, RoundingMode.HALF_UP),
                totalContributed.setScale(2, RoundingMode.HALF_UP),
                interestEarned.setScale(2, RoundingMode.HALF_UP));
    }

    public NpvResponse npv(NpvRequest request) {
        BigDecimal rate = request.discountRatePercent().divide(HUNDRED, MC);
        BigDecimal onePlusR = BigDecimal.ONE.add(rate, MC);
        BigDecimal npv = BigDecimal.ZERO;

        List<BigDecimal> flows = request.cashFlows();
        for (int t = 0; t < flows.size(); t++) {
            BigDecimal discountFactor = t == 0 ? BigDecimal.ONE : onePlusR.pow(t, MC);
            npv = npv.add(flows.get(t).divide(discountFactor, MC), MC);
        }
        return new NpvResponse(npv.setScale(2, RoundingMode.HALF_UP), request.discountRatePercent());
    }

    public TipSplitResponse tipSplit(TipSplitRequest request) {
        BigDecimal tax = request.taxPercent() == null
                ? MONEY_ZERO
                : request.billAmount().multiply(request.taxPercent(), MC).divide(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tip = request.billAmount().multiply(request.tipPercent(), MC).divide(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = request.billAmount().add(tax).add(tip).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perPerson = total.divide(BigDecimal.valueOf(request.numberOfPeople()), MC).setScale(2, RoundingMode.HALF_UP);

        return new TipSplitResponse(request.billAmount().setScale(2, RoundingMode.HALF_UP), tax, tip, total, perPerson);
    }
}
