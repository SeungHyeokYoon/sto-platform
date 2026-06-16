package com.sto.platform.controller;

import com.sto.platform.dto.HoldingResponse;
import com.sto.platform.dto.InvestorCreateRequest;
import com.sto.platform.dto.InvestorResponse;
import com.sto.platform.service.InvestorService;
import com.sto.platform.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/// 투자자 등록·조회 API (명세 6.1).
@RestController
@RequestMapping("/api/investors")
public class InvestorController {

    private final InvestorService investorService;
    private final QueryService queryService;

    public InvestorController(InvestorService investorService, QueryService queryService) {
        this.investorService = investorService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<InvestorResponse> register(@Valid @RequestBody InvestorCreateRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        InvestorResponse body = InvestorResponse.from(investorService.register(request));
        URI location = uriBuilder.path("/api/investors/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    public InvestorResponse get(@PathVariable Long id) {
        return InvestorResponse.from(investorService.get(id));
    }

    /// 투자자 보유 잔고 조회(명세 6.1).
    @GetMapping("/{id}/holdings")
    public List<HoldingResponse> holdings(@PathVariable Long id) {
        return queryService.holdings(id);
    }
}
