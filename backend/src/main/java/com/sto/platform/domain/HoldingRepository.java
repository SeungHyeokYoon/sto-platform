package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingRepository extends JpaRepository<Holding, HoldingId> {
    List<Holding> findBySecurityId(Long securityId);

    List<Holding> findByInvestorId(Long investorId);
}
