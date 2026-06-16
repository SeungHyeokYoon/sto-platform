package com.sto.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;

/// 청약 신청 요청.
public record SubscriptionRequest(
        @NotNull(message = "증권 id는 필수입니다")
        Long securityId,

        @NotNull(message = "투자자 id는 필수입니다")
        Long investorId,

        @NotNull(message = "신청 수량은 필수입니다")
        @Positive(message = "신청 수량은 0보다 커야 합니다")
        BigInteger amount
) {
}
