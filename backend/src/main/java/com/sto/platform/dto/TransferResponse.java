package com.sto.platform.dto;

import java.math.BigInteger;

/// 권리이전 결과(온체인 트랜잭션 기준). DB 명부는 이후 이벤트 동기화로 반영된다.
public record TransferResponse(
        Long securityId,
        Long fromInvestorId,
        Long toInvestorId,
        BigInteger amount,
        String txHash,
        BigInteger blockNumber,
        String status
) {
}
