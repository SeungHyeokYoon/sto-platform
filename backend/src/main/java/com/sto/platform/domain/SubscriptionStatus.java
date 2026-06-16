package com.sto.platform.domain;

/// 청약 상태. PENDING(신청) → ALLOCATED(배정완료) / REJECTED(반려).
public enum SubscriptionStatus {
    PENDING,
    ALLOCATED,
    REJECTED
}
