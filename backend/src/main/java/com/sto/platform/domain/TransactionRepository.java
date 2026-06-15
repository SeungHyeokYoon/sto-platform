package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    Optional<TransactionRecord> findByTxHash(String txHash);

    boolean existsByTxHash(String txHash);

    List<TransactionRecord> findBySecurityIdOrderByBlockNumberDesc(Long securityId);
}
