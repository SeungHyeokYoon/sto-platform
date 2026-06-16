package com.sto.platform.controller;

import com.sto.platform.dto.ReconcileReport;
import com.sto.platform.sync.ReconcileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 수동 대사 트리거 API (명세 6.1).
@RestController
public class ReconciliationController {

    private final ReconcileService reconcileService;

    public ReconciliationController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    /// securityId가 주어지면 해당 증권만, 아니면 전체 대사.
    @PostMapping("/api/reconciliation/run")
    public List<ReconcileReport> run(@RequestParam(required = false) Long securityId) {
        if (securityId != null) {
            return List.of(reconcileService.reconcile(securityId));
        }
        return reconcileService.reconcileAll();
    }
}
