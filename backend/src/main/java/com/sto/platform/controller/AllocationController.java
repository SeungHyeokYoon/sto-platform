package com.sto.platform.controller;

import com.sto.platform.dto.AllocationRequest;
import com.sto.platform.dto.AllocationResponse;
import com.sto.platform.service.AllocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/// 배정 API (명세 6.1).
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationResponse allocate(@Valid @RequestBody AllocationRequest request) {
        return allocationService.allocate(request);
    }
}
