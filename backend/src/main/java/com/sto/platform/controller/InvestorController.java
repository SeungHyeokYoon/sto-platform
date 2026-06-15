package com.sto.platform.controller;

import com.sto.platform.dto.InvestorCreateRequest;
import com.sto.platform.dto.InvestorResponse;
import com.sto.platform.service.InvestorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/// 투자자 등록·조회 API (명세 6.1).
@RestController
@RequestMapping("/api/investors")
public class InvestorController {

    private final InvestorService investorService;

    public InvestorController(InvestorService investorService) {
        this.investorService = investorService;
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
}
