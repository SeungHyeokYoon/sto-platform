package com.sto.platform.dto;

import jakarta.validation.constraints.NotNull;

/// 증권별 whitelist(KYC 거래 허가) 등록/해제 요청.
public record WhitelistRequest(
        @NotNull(message = "투자자 id는 필수입니다")
        Long investorId,

        /// 등록(true)/해제(false). 미지정 시 등록으로 본다.
        Boolean status
) {
    public boolean statusOrDefault() {
        return status == null || status;
    }
}
