package com.sto.platform.dto;

import java.util.List;

/// 정합성 대사 결과.
public record ReconcileReport(
        Long securityId,
        int checkedHolders,
        int mismatches,
        int corrected,
        List<String> details
) {
}
