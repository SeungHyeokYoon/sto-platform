package com.sto.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/// 투자자 등록 요청.
public record InvestorCreateRequest(
        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotBlank(message = "지갑 주소는 필수입니다")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "유효한 EVM 지갑 주소가 아닙니다")
        String walletAddress
) {
}
