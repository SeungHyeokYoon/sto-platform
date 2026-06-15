package com.sto.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncCheckpointRepository extends JpaRepository<SyncCheckpoint, Long> {
}
