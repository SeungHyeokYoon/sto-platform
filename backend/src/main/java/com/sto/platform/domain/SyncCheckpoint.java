package com.sto.platform.domain;

import jakarta.persistence.*;

/// 증권별 동기화 체크포인트. 마지막으로 처리한 블록 번호 저장(명세 7.3 누락 복구).
/// 명세 6.2: sync_checkpoint(security_id PK, last_processed_block)
@Entity
@Table(name = "sync_checkpoint")
public class SyncCheckpoint {

    @Id
    @Column(name = "security_id")
    private Long securityId;

    @Column(name = "last_processed_block", nullable = false)
    private Long lastProcessedBlock = 0L;

    protected SyncCheckpoint() {
    }

    public SyncCheckpoint(Long securityId, Long lastProcessedBlock) {
        this.securityId = securityId;
        this.lastProcessedBlock = lastProcessedBlock;
    }

    public Long getSecurityId() {
        return securityId;
    }

    public Long getLastProcessedBlock() {
        return lastProcessedBlock;
    }

    public void setLastProcessedBlock(Long lastProcessedBlock) {
        this.lastProcessedBlock = lastProcessedBlock;
    }
}
