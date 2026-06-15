package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestorRepository extends JpaRepository<Investor, Long> {
    Optional<Investor> findByWalletAddress(String walletAddress);

    boolean existsByWalletAddress(String walletAddress);
}
