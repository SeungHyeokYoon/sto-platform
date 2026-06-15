package com.sto.platform.domain;

import jakarta.persistence.*;

import java.math.BigInteger;

/// 투자자별·증권별 보유 잔고(온체인 balanceOf의 오프체인 미러).
/// 명세 6.2: holding(investor_id FK, security_id FK, balance, PK(investor_id, security_id))
@Entity
@Table(name = "holding")
@IdClass(HoldingId.class)
public class Holding {

    @Id
    @Column(name = "investor_id")
    private Long investorId;

    @Id
    @Column(name = "security_id")
    private Long securityId;

    @Column(nullable = false, precision = 78, scale = 0)
    private BigInteger balance = BigInteger.ZERO;

    protected Holding() {
    }

    public Holding(Long investorId, Long securityId, BigInteger balance) {
        this.investorId = investorId;
        this.securityId = securityId;
        this.balance = balance;
    }

    public Long getInvestorId() {
        return investorId;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }
}
