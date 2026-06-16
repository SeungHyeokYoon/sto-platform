package com.sto.platform.controller;

import com.sto.platform.dto.TransactionResponse;
import com.sto.platform.service.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 거래내역 조회 API (명세 6.1).
@RestController
public class TransactionController {

    private final QueryService queryService;

    public TransactionController(QueryService queryService) {
        this.queryService = queryService;
    }

    /// 거래내역. securityId 파라미터로 증권 필터 가능.
    @GetMapping("/api/transactions")
    public List<TransactionResponse> transactions(@RequestParam(required = false) Long securityId) {
        return queryService.transactions(securityId);
    }
}
