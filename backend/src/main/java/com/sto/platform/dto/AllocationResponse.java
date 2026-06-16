package com.sto.platform.dto;

import com.sto.platform.domain.SubscriptionStatus;

import java.math.BigInteger;

/// 배정 결과(온체인 이전 기준). DB 명부는 이후 이벤트 동기화로 반영된다.
public record AllocationResponse(
        Long subscriptionId,
        Long securityId,
        Long investorId,
        String walletAddress,
        BigInteger allocatedAmount,
        SubscriptionStatus status,
        String txHash,
        BigInteger blockNumber
) {
}
