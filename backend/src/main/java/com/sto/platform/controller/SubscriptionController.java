package com.sto.platform.controller;

import com.sto.platform.dto.SubscriptionRequest;
import com.sto.platform.dto.SubscriptionResponse;
import com.sto.platform.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/// 청약 신청 API (명세 6.1).
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request,
                                                          UriComponentsBuilder uriBuilder) {
        SubscriptionResponse body = SubscriptionResponse.from(subscriptionService.subscribe(request));
        URI location = uriBuilder.path("/api/subscriptions/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }
}
