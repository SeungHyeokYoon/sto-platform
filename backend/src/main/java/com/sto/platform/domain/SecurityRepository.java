package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecurityRepository extends JpaRepository<Security, Long> {
    Optional<Security> findByContractAddress(String contractAddress);
}
