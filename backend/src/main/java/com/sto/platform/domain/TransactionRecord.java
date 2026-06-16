package com.sto.platform.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigInteger;
import java.time.Instant;

/// 온체인 이벤트에서 파생된 거래내역. tx_hash UNIQUE로 멱등성 보장(명세 7.2).
/// 명세 6.2: transaction(id PK, security_id FK, tx_hash UNIQUE, block_number, type,
///                       from_addr, to_addr, amount, status, created_at)
@Entity
@Table(name = "transaction")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "security_id", nullable = false)
    private Long securityId;

    @Column(name = "tx_hash", nullable = false, unique = true, length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TxType type;

    @Column(name = "from_addr", length = 42)
    private String fromAddr;

    @Column(name = "to_addr", length = 42)
    private String toAddr;

    @Column(nullable = false, precision = 78, scale = 0)
    private BigInteger amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TxStatus status = TxStatus.CONFIRMED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionRecord() {
    }

    public TransactionRecord(Long securityId, String txHash, Long blockNumber, TxType type,
                             String fromAddr, String toAddr, BigInteger amount, TxStatus status) {
        this.securityId = securityId;
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.type = type;
        this.fromAddr = fromAddr;
        this.toAddr = toAddr;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public String getTxHash() {
        return txHash;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public TxType getType() {
        return type;
    }

    public String getFromAddr() {
        return fromAddr;
    }

    public String getToAddr() {
        return toAddr;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public TxStatus getStatus() {
        return status;
    }

    public void setStatus(TxStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
