package com.sto.platform.dto;

import com.sto.platform.domain.Security;

import java.math.BigInteger;
import java.time.Instant;

/// 증권 응답.
public record SecurityResponse(
        Long id,
        String name,
        String symbol,
        String contractAddress,
        BigInteger totalSupply,
        BigInteger maxSupply,
        Instant createdAt
) {
    public static SecurityResponse from(Security security) {
        return new SecurityResponse(
                security.getId(),
                security.getName(),
                security.getSymbol(),
                security.getContractAddress(),
                security.getTotalSupply(),
                security.getMaxSupply(),
                security.getCreatedAt()
        );
    }
}
