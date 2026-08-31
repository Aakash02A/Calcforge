package com.calcforge.controller.local;

import com.calcforge.dto.request.CompoundInterestRequest;
import com.calcforge.dto.request.LoanRequest;
import com.calcforge.dto.request.NpvRequest;
import com.calcforge.dto.request.TipSplitRequest;
import com.calcforge.dto.response.CompoundInterestResponse;
import com.calcforge.dto.response.LoanResponse;
import com.calcforge.dto.response.NpvResponse;
import com.calcforge.dto.response.TipSplitResponse;
import com.calcforge.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @PostMapping("/loan")
    public LoanResponse loan(@Valid @RequestBody LoanRequest request) {
        return financeService.amortize(request);
    }

    @PostMapping("/compound-interest")
    public CompoundInterestResponse compoundInterest(@Valid @RequestBody CompoundInterestRequest request) {
        return financeService.compoundInterest(request);
    }

    @PostMapping("/npv")
    public NpvResponse npv(@Valid @RequestBody NpvRequest request) {
        return financeService.npv(request);
    }

    @PostMapping("/tip-split")
    public TipSplitResponse tipSplit(@Valid @RequestBody TipSplitRequest request) {
        return financeService.tipSplit(request);
    }
}
