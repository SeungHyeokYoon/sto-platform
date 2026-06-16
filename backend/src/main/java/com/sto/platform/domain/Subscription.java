package com.sto.platform.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigInteger;
import java.time.Instant;

/// 청약 신청(명세 ②). 명세 6.2 데이터모델 확장(subscription 테이블 추가).
/// 신청(PENDING) 후 배정 시 allocatedAmount/status 갱신.
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    @Column(name = "investor_id", nullable = false)
    private Long investorId;

    @Column(name = "requested_amount", nullable = false, precision = 78, scale = 0)
    private BigInteger requestedAmount;

    @Column(name = "allocated_amount", precision = 78, scale = 0)
    private BigInteger allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Subscription() {
    }

    public Subscription(Long securityId, Long investorId, BigInteger requestedAmount) {
        this.securityId = securityId;
        this.investorId = investorId;
        this.requestedAmount = requestedAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public Long getInvestorId() {
        return investorId;
    }

    public BigInteger getRequestedAmount() {
        return requestedAmount;
    }

    public BigInteger getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigInteger allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
