package com.sto.platform.dto;

/// 증권별 whitelist 처리 결과.
public record WhitelistResponse(
        Long securityId,
        Long investorId,
        String walletAddress,
        boolean whitelisted,
        String txHash
) {
}
