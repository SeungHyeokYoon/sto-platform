package com.sto.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;

/// 권리이전 요청(투자자 → 투자자, 계좌관리기관 대행).
public record TransferRequest(
        @NotNull(message = "증권 id는 필수입니다")
        Long securityId,

        @NotNull(message = "보내는 투자자 id는 필수입니다")
        Long fromInvestorId,

        @NotNull(message = "받는 투자자 id는 필수입니다")
        Long toInvestorId,

        @NotNull(message = "이전 수량은 필수입니다")
        @Positive(message = "이전 수량은 0보다 커야 합니다")
        BigInteger amount
) {
}
