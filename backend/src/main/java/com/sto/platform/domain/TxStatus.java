package com.sto.platform.domain;

/// 거래 반영 상태. 온체인 확정 시 CONFIRMED.
public enum TxStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
