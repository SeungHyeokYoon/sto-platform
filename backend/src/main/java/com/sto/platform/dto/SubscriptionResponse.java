package com.sto.platform.dto;

import com.sto.platform.domain.Subscription;
import com.sto.platform.domain.SubscriptionStatus;

import java.math.BigInteger;
import java.time.Instant;

/// 청약 응답.
public record SubscriptionResponse(
        Long id,
        Long securityId,
        Long investorId,
        BigInteger requestedAmount,
        BigInteger allocatedAmount,
        SubscriptionStatus status,
        Instant createdAt
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getId(), s.getSecurityId(), s.getInvestorId(),
                s.getRequestedAmount(), s.getAllocatedAmount(), s.getStatus(), s.getCreatedAt());
    }
}
