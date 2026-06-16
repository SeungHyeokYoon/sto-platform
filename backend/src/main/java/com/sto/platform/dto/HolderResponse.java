package com.sto.platform.dto;

import java.math.BigInteger;

/// 권리자명부 항목(증권별 보유자).
public record HolderResponse(
        Long investorId,
        String name,
        String walletAddress,
        BigInteger balance
) {
}
