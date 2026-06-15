package com.sto.platform.dto;

import com.sto.platform.domain.Investor;
import com.sto.platform.domain.KycStatus;

import java.time.Instant;

/// 투자자 응답.
public record InvestorResponse(
        Long id,
        String name,
        String walletAddress,
        KycStatus kycStatus,
        Instant createdAt
) {
    public static InvestorResponse from(Investor investor) {
        return new InvestorResponse(
                investor.getId(),
                investor.getName(),
                investor.getWalletAddress(),
                investor.getKycStatus(),
                investor.getCreatedAt()
        );
    }
}
