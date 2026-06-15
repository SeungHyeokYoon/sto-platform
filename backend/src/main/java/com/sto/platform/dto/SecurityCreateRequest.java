package com.sto.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;

/// 증권 등록 요청. 이미 배포된 SecurityToken 컨트랙트 주소를 연결한다.
public record SecurityCreateRequest(
        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotBlank(message = "심볼은 필수입니다")
        String symbol,

        @NotNull(message = "발행한도는 필수입니다")
        @Positive(message = "발행한도는 0보다 커야 합니다")
        BigInteger maxSupply,

        @NotBlank(message = "컨트랙트 주소는 필수입니다")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "유효한 컨트랙트 주소가 아닙니다")
        String contractAddress
) {
}
