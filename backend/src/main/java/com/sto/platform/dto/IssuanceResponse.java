package com.sto.platform.dto;

import java.math.BigInteger;

/// 발행 결과(온체인 트랜잭션 기준). DB 명부는 이후 이벤트 동기화로 반영된다.
public record IssuanceResponse(
        Long securityId,
        Long investorId,
        String walletAddress,
        BigInteger amount,
        String txHash,
        BigInteger blockNumber,
        String status
) {
}
