package com.sto.platform.dto;

import com.sto.platform.domain.TransactionRecord;
import com.sto.platform.domain.TxStatus;
import com.sto.platform.domain.TxType;

import java.math.BigInteger;
import java.time.Instant;

/// 거래내역 항목.
public record TransactionResponse(
        Long id,
        Long securityId,
        String txHash,
        Long blockNumber,
        TxType type,
        String fromAddr,
        String toAddr,
        BigInteger amount,
        TxStatus status,
        Instant createdAt
) {
    public static TransactionResponse from(TransactionRecord t) {
        return new TransactionResponse(
                t.getId(), t.getSecurityId(), t.getTxHash(), t.getBlockNumber(),
                t.getType(), t.getFromAddr(), t.getToAddr(), t.getAmount(),
                t.getStatus(), t.getCreatedAt());
    }
}
