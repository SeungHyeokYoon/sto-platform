package com.sto.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;

/// 발행(mint) 요청.
public record IssuanceRequest(
        @NotNull(message = "증권 id는 필수입니다")
        Long securityId,

        @NotNull(message = "투자자 id는 필수입니다")
        Long investorId,

        @NotNull(message = "발행 수량은 필수입니다")
        @Positive(message = "발행 수량은 0보다 커야 합니다")
        BigInteger amount
) {
}
