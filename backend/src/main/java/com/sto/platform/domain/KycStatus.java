package com.sto.platform.domain;

/// 투자자 KYC 상태. APPROVED만 온체인 whitelist 등록 대상.
public enum KycStatus {
    PENDING,
    APPROVED,
    REJECTED
}
