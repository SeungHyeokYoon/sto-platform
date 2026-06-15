package com.sto.platform.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigInteger;
import java.time.Instant;

/// 증권(토큰). contract_address로 온체인 SecurityToken과 연결.
/// 명세 6.2: security(id PK, name, symbol, contract_address, total_supply, max_supply, created_at)
@Entity
@Table(name = "security")
public class Security {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "contract_address", unique = true, length = 42)
    private String contractAddress;

    // 온체인 uint256 대응 → NUMERIC(78,0)
    @Column(name = "total_supply", nullable = false, precision = 78, scale = 0)
    private BigInteger totalSupply = BigInteger.ZERO;

    @Column(name = "max_supply", nullable = false, precision = 78, scale = 0)
    private BigInteger maxSupply;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Security() {
    }

    public Security(String name, String symbol, BigInteger maxSupply) {
        this.name = name;
        this.symbol = symbol;
        this.maxSupply = maxSupply;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public BigInteger getTotalSupply() {
        return totalSupply;
    }

    public void setTotalSupply(BigInteger totalSupply) {
        this.totalSupply = totalSupply;
    }

    public BigInteger getMaxSupply() {
        return maxSupply;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
