package com.sto.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;

/// 배정 요청. 청약 건에 대해 배정량을 결정한다.
public record AllocationRequest(
        @NotNull(message = "청약 id는 필수입니다")
        Long subscriptionId,

        @NotNull(message = "배정 수량은 필수입니다")
        @Positive(message = "배정 수량은 0보다 커야 합니다")
        BigInteger amount
) {
}
