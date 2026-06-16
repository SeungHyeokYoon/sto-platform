package com.sto.platform.dto;

import java.math.BigInteger;

/// 투자자별 보유 항목(증권별 잔고).
public record HoldingResponse(
        Long securityId,
        String symbol,
        String name,
        BigInteger balance
) {
}
