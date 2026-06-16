package com.sto.platform.sync;

import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.*;
import com.sto.platform.dto.ReconcileReport;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/// 정합성 검증·복구(명세 ⑤, 7.4). 온체인 balanceOf ↔ DB holding 대사.
/// 절차: 먼저 checkpoint부터 이벤트 재조회(누락 복구) → 재대사 → 남은 불일치는
/// 온체인(정본) 값으로 DB를 보정한다.
@Service
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final SyncService syncService;
    private final SecurityRepository securityRepository;
    private final InvestorRepository investorRepository;
    private final HoldingRepository holdingRepository;
    private final ChainService chainService;

    public ReconcileService(SyncService syncService,
                            SecurityRepository securityRepository,
                            InvestorRepository investorRepository,
                            HoldingRepository holdingRepository,
                            ChainService chainService) {
        this.syncService = syncService;
        this.securityRepository = securityRepository;
        this.investorRepository = investorRepository;
        this.holdingRepository = holdingRepository;
        this.chainService = chainService;
    }

    public List<ReconcileReport> reconcileAll() {
        List<ReconcileReport> reports = new ArrayList<>();
        for (Security security : securityRepository.findAll()) {
            if (security.getContractAddress() != null && !security.getContractAddress().isBlank()) {
                reports.add(reconcile(security.getId()));
            }
        }
        return reports;
    }

    @Transactional
    public ReconcileReport reconcile(Long securityId) {
        Security security = securityRepository.findById(securityId)
                .orElseThrow(() -> new NotFoundException("증권을 찾을 수 없습니다: " + securityId));
        String contract = security.getContractAddress();

        // 1) 누락 이벤트 재조회로 1차 복구
        syncService.syncSecurity(securityId);

        // 총발행량 미러 보정(온체인 정본)
        security.setTotalSupply(chainService.totalSupply(contract));

        // 2) 보유자별 대사
        List<String> details = new ArrayList<>();
        int checked = 0;
        int mismatches = 0;
        int corrected = 0;

        for (Investor investor : investorRepository.findAll()) {
            checked++;
            BigInteger onChain = chainService.balanceOf(contract, investor.getWalletAddress());
            HoldingId id = new HoldingId(investor.getId(), securityId);
            BigInteger db = holdingRepository.findById(id)
                    .map(Holding::getBalance).orElse(BigInteger.ZERO);

            if (onChain.compareTo(db) != 0) {
                mismatches++;
                String msg = "불일치 investorId=%d wallet=%s db=%s onChain=%s → 보정"
                        .formatted(investor.getId(), investor.getWalletAddress(), db, onChain);
                log.warn(msg);
                details.add(msg);

                // 3) 온체인(정본) 값으로 보정
                Holding h = holdingRepository.findById(id)
                        .orElseGet(() -> new Holding(investor.getId(), securityId, BigInteger.ZERO));
                h.setBalance(onChain);
                holdingRepository.save(h);
                corrected++;
            }
        }

        log.info("대사 완료 securityId={} 검사={} 불일치={} 보정={}", securityId, checked, mismatches, corrected);
        return new ReconcileReport(securityId, checked, mismatches, corrected, details);
    }
}
