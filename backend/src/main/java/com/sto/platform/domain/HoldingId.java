package com.sto.platform.domain;

import java.io.Serializable;
import java.util.Objects;

/// Holding 복합 키 (investor_id, security_id).
public class HoldingId implements Serializable {

    private Long investorId;
    private Long securityId;

    public HoldingId() {
    }

    public HoldingId(Long investorId, Long securityId) {
        this.investorId = investorId;
        this.securityId = securityId;
    }

    public Long getInvestorId() {
        return investorId;
    }

    public Long getSecurityId() {
        return securityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingId that)) return false;
        return Objects.equals(investorId, that.investorId)
                && Objects.equals(securityId, that.securityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(investorId, securityId);
    }
}
