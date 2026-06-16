package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findBySecurityId(Long securityId);

    List<Subscription> findByInvestorId(Long investorId);
}
