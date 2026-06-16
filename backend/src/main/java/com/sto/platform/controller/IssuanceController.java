package com.sto.platform.controller;

import com.sto.platform.dto.IssuanceRequest;
import com.sto.platform.dto.IssuanceResponse;
import com.sto.platform.service.IssuanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/// 발행(mint) API (명세 6.1).
@RestController
@RequestMapping("/api/issuance")
public class IssuanceController {

    private final IssuanceService issuanceService;

    public IssuanceController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuanceResponse issue(@Valid @RequestBody IssuanceRequest request) {
        return issuanceService.issue(request);
    }
}
