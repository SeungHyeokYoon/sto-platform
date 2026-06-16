package com.sto.platform.sync;

import com.sto.platform.chain.contracts.SecurityToken;
import com.sto.platform.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;

/// 온체인 이벤트 → DB 미러 반영(명세 ④, 7장). 폴링 방식: checkpoint 이후 블록의
/// 로그를 조회해 반영하고 checkpoint를 전진시킨다. 방향은 항상 온체인→DB 단방향.
/// 멱등성: transaction.tx_hash UNIQUE + 반영 전 존재 확인으로 중복 반영 방지.
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final String ISSUED_TOPIC = EventEncoder.encode(SecurityToken.ISSUED_EVENT);
    private static final String TRANSFERRED_TOPIC = EventEncoder.encode(SecurityToken.TRANSFERRED_EVENT);

    private final Web3j web3j;
    private final SecurityRepository securityRepository;
    private final InvestorRepository investorRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final SyncCheckpointRepository checkpointRepository;

    public SyncService(Web3j web3j,
                       SecurityRepository securityRepository,
                       InvestorRepository investorRepository,
                       HoldingRepository holdingRepository,
                       TransactionRepository transactionRepository,
                       SyncCheckpointRepository checkpointRepository) {
        this.web3j = web3j;
        this.securityRepository = securityRepository;
        this.investorRepository = investorRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.checkpointRepository = checkpointRepository;
    }

    /// 컨트랙트 주소가 연결된 모든 증권을 동기화한다.
    public int syncAll() {
        int total = 0;
        for (Security security : securityRepository.findAll()) {
            if (security.getContractAddress() != null && !security.getContractAddress().isBlank()) {
                total += syncSecurity(security.getId());
            }
        }
        return total;
    }

    /// 단일 증권 동기화. 반영한 신규 이벤트 수를 반환한다.
    @Transactional
    public int syncSecurity(Long securityId) {
        Security security = securityRepository.findById(securityId).orElseThrow();
        long fromBlock = checkpoint(securityId) + 1;
        long latest = latestBlock();
        if (fromBlock > latest) {
            return 0;
        }

        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(latest)),
                security.getContractAddress());

        List<EthLog.LogResult> logs = getLogs(filter);
        int applied = 0;
        for (EthLog.LogResult<?> result : logs) {
            Log evlog = (Log) result.get();
            if (applyLog(security, evlog)) {
                applied++;
            }
        }

        saveCheckpoint(securityId, latest);
        if (applied > 0) {
            log.info("동기화 securityId={} 블록 {}~{} 신규이벤트 {}건", securityId, fromBlock, latest, applied);
        }
        return applied;
    }

    /// 단일 로그를 DB에 반영. 이미 반영된 tx면 false(멱등).
    private boolean applyLog(Security security, Log evlog) {
        String topic0 = evlog.getTopics().get(0);
        String txHash = evlog.getTransactionHash();
        if (transactionRepository.existsByTxHash(txHash)) {
            return false; // 멱등: 이미 반영됨
        }
        long blockNumber = evlog.getBlockNumber().longValue();

        if (ISSUED_TOPIC.equals(topic0)) {
            EventValues ev = Contract.staticExtractEventParameters(SecurityToken.ISSUED_EVENT, evlog);
            String to = ((Address) ev.getIndexedValues().get(0)).getValue();
            BigInteger amount = ((Uint256) ev.getNonIndexedValues().get(0)).getValue();
            applyIssue(security, to, amount, txHash, blockNumber);
            return true;
        } else if (TRANSFERRED_TOPIC.equals(topic0)) {
            EventValues ev = Contract.staticExtractEventParameters(SecurityToken.TRANSFERRED_EVENT, evlog);
            String from = ((Address) ev.getIndexedValues().get(0)).getValue();
            String to = ((Address) ev.getIndexedValues().get(1)).getValue();
            BigInteger amount = ((Uint256) ev.getNonIndexedValues().get(0)).getValue();
            applyTransfer(security, from, to, amount, txHash, blockNumber);
            return true;
        }
        return false; // 그 외 이벤트(WhitelistUpdated 등)는 명부 미러에 불필요
    }

    private void applyIssue(Security security, String to, BigInteger amount, String txHash, long block) {
        transactionRepository.save(new TransactionRecord(
                security.getId(), txHash, block, TxType.ISSUE, null, to, amount, TxStatus.CONFIRMED));
        security.setTotalSupply(security.getTotalSupply().add(amount));
        creditHolding(to, security.getId(), amount);
    }

    private void applyTransfer(Security security, String from, String to, BigInteger amount,
                               String txHash, long block) {
        transactionRepository.save(new TransactionRecord(
                security.getId(), txHash, block, TxType.TRANSFER, from, to, amount, TxStatus.CONFIRMED));
        debitHolding(from, security.getId(), amount);
        creditHolding(to, security.getId(), amount);
    }

    /// 보유 증가. 등록된 투자자 주소에만 반영(창고/agent 등 비투자자 주소는 명부 대상 아님).
    private void creditHolding(String wallet, Long securityId, BigInteger amount) {
        investorRepository.findByWalletAddress(wallet.toLowerCase()).ifPresent(inv -> {
            Holding h = holdingRepository.findById(new HoldingId(inv.getId(), securityId))
                    .orElseGet(() -> new Holding(inv.getId(), securityId, BigInteger.ZERO));
            h.setBalance(h.getBalance().add(amount));
            holdingRepository.save(h);
        });
    }

    private void debitHolding(String wallet, Long securityId, BigInteger amount) {
        investorRepository.findByWalletAddress(wallet.toLowerCase()).ifPresent(inv -> {
            Holding h = holdingRepository.findById(new HoldingId(inv.getId(), securityId))
                    .orElseGet(() -> new Holding(inv.getId(), securityId, BigInteger.ZERO));
            h.setBalance(h.getBalance().subtract(amount));
            holdingRepository.save(h);
        });
    }

    private long checkpoint(Long securityId) {
        return checkpointRepository.findById(securityId)
                .map(SyncCheckpoint::getLastProcessedBlock)
                .orElse(0L);
    }

    private void saveCheckpoint(Long securityId, long block) {
        SyncCheckpoint cp = checkpointRepository.findById(securityId)
                .orElseGet(() -> new SyncCheckpoint(securityId, 0L));
        cp.setLastProcessedBlock(block);
        checkpointRepository.save(cp);
    }

    private long latestBlock() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (Exception e) {
            throw new com.sto.platform.chain.ChainException("최신 블록 조회 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EthLog.LogResult> getLogs(EthFilter filter) {
        try {
            return web3j.ethGetLogs(filter).send().getLogs();
        } catch (Exception e) {
            throw new com.sto.platform.chain.ChainException("이벤트 로그 조회 실패", e);
        }
    }
}
