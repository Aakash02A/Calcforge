package com.calcforge.service;

import com.calcforge.dto.request.CompoundInterestRequest;
import com.calcforge.dto.request.LoanRequest;
import com.calcforge.dto.request.NpvRequest;
import com.calcforge.dto.request.TipSplitRequest;
import com.calcforge.dto.response.CompoundInterestResponse;
import com.calcforge.dto.response.LoanResponse;
import com.calcforge.dto.response.NpvResponse;
import com.calcforge.dto.response.TipSplitResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceServiceTest {

    private final FinanceService financeService = new FinanceService();

    @Test
    void zeroInterestLoanSplitsPrincipalEvenly() {
        LoanRequest request = new LoanRequest(BigDecimal.valueOf(1200), BigDecimal.ZERO, 12, true);
        LoanResponse response = financeService.amortize(request);

        assertEquals(0, new BigDecimal("100.00").compareTo(response.monthlyPayment()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(response.totalPayment()));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(response.totalInterest()));
        assertEquals(12, response.schedule().size());
        // Final balance must land exactly on zero, not drift due to rounding.
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                response.schedule().get(response.schedule().size() - 1).remainingBalance()));
    }

    @Test
    void loanScheduleFullyAmortizes() {
        LoanRequest request = new LoanRequest(BigDecimal.valueOf(350000), BigDecimal.valueOf(4.75), 360, true);
        LoanResponse response = financeService.amortize(request);

        assertEquals(360, response.schedule().size());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                response.schedule().get(359).remainingBalance()));
        // Total paid should exceed principal (interest was charged) but stay in a sane range.
        assertTrue(response.totalPayment().compareTo(request.principal()) > 0);
    }

    @Test
    void tipSplitBasicMath() {
        TipSplitRequest request = new TipSplitRequest(BigDecimal.valueOf(100), BigDecimal.valueOf(20), 4, null);
        TipSplitResponse response = financeService.tipSplit(request);

        assertEquals(0, new BigDecimal("20.00").compareTo(response.tipAmount()));
        assertEquals(0, new BigDecimal("120.00").compareTo(response.totalAmount()));
        assertEquals(0, new BigDecimal("30.00").compareTo(response.perPersonAmount()));
    }

    @Test
    void tipSplitWithTax() {
        TipSplitRequest request = new TipSplitRequest(BigDecimal.valueOf(100), BigDecimal.valueOf(15), 2, BigDecimal.valueOf(8));
        TipSplitResponse response = financeService.tipSplit(request);

        assertEquals(0, new BigDecimal("8.00").compareTo(response.taxAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(response.tipAmount()));
        assertEquals(0, new BigDecimal("123.00").compareTo(response.totalAmount()));
        assertEquals(0, new BigDecimal("61.50").compareTo(response.perPersonAmount()));
    }

    @Test
    void zeroInterestCompoundGrowthIsJustPrincipalPlusContributions() {
        CompoundInterestRequest request = new CompoundInterestRequest(
                BigDecimal.valueOf(1000), BigDecimal.ZERO, 12, BigDecimal.ONE, BigDecimal.ZERO);
        CompoundInterestResponse response = financeService.compoundInterest(request);

        assertEquals(0, new BigDecimal("1000.00").compareTo(response.futureValue()));
        assertEquals(0, new BigDecimal("0.00").compareTo(response.totalInterestEarned()));
    }

    @Test
    void compoundGrowthWithPositiveRateExceedsPrincipal() {
        CompoundInterestRequest request = new CompoundInterestRequest(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(5), 12, BigDecimal.ONE, BigDecimal.ZERO);
        CompoundInterestResponse response = financeService.compoundInterest(request);

        assertTrue(response.futureValue().compareTo(BigDecimal.valueOf(1000)) > 0);
        assertTrue(response.totalInterestEarned().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void npvOfBreakEvenProjectIsZero() {
        // -100 now, +110 in one year, discounted at 10% => exactly break-even.
        NpvRequest request = new NpvRequest(BigDecimal.TEN,
                List.of(BigDecimal.valueOf(-100), BigDecimal.valueOf(110)));
        NpvResponse response = financeService.npv(request);

        assertEquals(0, new BigDecimal("0.00").compareTo(response.netPresentValue()));
    }
}
