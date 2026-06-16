package com.sto.platform.controller;

import com.sto.platform.dto.SecurityCreateRequest;
import com.sto.platform.dto.SecurityResponse;
import com.sto.platform.dto.WhitelistRequest;
import com.sto.platform.dto.WhitelistResponse;
import com.sto.platform.service.SecurityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/// 증권(종목) 등록·조회 API (명세 6.1).
@RestController
@RequestMapping("/api/securities")
public class SecurityController {

    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @PostMapping
    public ResponseEntity<SecurityResponse> register(@Valid @RequestBody SecurityCreateRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        SecurityResponse body = SecurityResponse.from(securityService.register(request));
        URI location = uriBuilder.path("/api/securities/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    public SecurityResponse get(@PathVariable Long id) {
        return SecurityResponse.from(securityService.get(id));
    }

    /// 증권별 whitelist(거래 허가) 등록/해제. 발행·이전의 선행 조건.
    @PostMapping("/{id}/whitelist")
    public WhitelistResponse setWhitelist(@PathVariable Long id,
                                          @Valid @RequestBody WhitelistRequest request) {
        return securityService.setWhitelist(id, request);
    }
}
