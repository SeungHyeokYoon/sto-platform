package com.sto.platform.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// 주기 폴링으로 온체인 이벤트를 DB에 반영(명세 7.1 실시간 동기화).
/// sto.sync.scheduling-enabled=false 면 비활성(테스트 등).
@Component
@ConditionalOnProperty(name = "sto.sync.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${sto.sync.poll-interval-ms:2000}")
    public void poll() {
        try {
            syncService.syncAll();
        } catch (Exception e) {
            // 노드 일시 장애 등은 다음 주기에 checkpoint부터 재조회로 복구된다.
            log.warn("동기화 폴링 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }
}
