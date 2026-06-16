package com.sto.platform.service;

import com.sto.platform.domain.*;
import com.sto.platform.dto.HolderResponse;
import com.sto.platform.dto.HoldingResponse;
import com.sto.platform.dto.TransactionResponse;
import com.sto.platform.error.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/// 조회 전용 서비스(명세 6.1 GET). DB 미러(holding/transaction)를 읽는다.
/// 미러는 3주차 이벤트 동기화로 채워진다(그 전에는 빈 결과).
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final SecurityRepository securityRepository;
    private final InvestorRepository investorRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    public QueryService(SecurityRepository securityRepository,
                        InvestorRepository investorRepository,
                        HoldingRepository holdingRepository,
                        TransactionRepository transactionRepository) {
        this.securityRepository = securityRepository;
        this.investorRepository = investorRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
    }

    /// 증권별 권리자명부(잔고 0 초과 보유자).
    public List<HolderResponse> holders(Long securityId) {
        if (!securityRepository.existsById(securityId)) {
            throw new NotFoundException("증권을 찾을 수 없습니다: " + securityId);
        }
        return holdingRepository.findBySecurityId(securityId).stream()
                .filter(h -> h.getBalance().signum() > 0)
                .map(h -> {
                    Investor inv = investorRepository.findById(h.getInvestorId()).orElse(null);
                    return new HolderResponse(
                            h.getInvestorId(),
                            inv != null ? inv.getName() : null,
                            inv != null ? inv.getWalletAddress() : null,
                            h.getBalance());
                })
                .toList();
    }

    /// 투자자별 보유 잔고.
    public List<HoldingResponse> holdings(Long investorId) {
        if (!investorRepository.existsById(investorId)) {
            throw new NotFoundException("투자자를 찾을 수 없습니다: " + investorId);
        }
        return holdingRepository.findByInvestorId(investorId).stream()
                .map(h -> {
                    Security sec = securityRepository.findById(h.getSecurityId()).orElse(null);
                    return new HoldingResponse(
                            h.getSecurityId(),
                            sec != null ? sec.getSymbol() : null,
                            sec != null ? sec.getName() : null,
                            h.getBalance());
                })
                .toList();
    }

    /// 거래내역. securityId가 주어지면 해당 증권만, 아니면 전체.
    public List<TransactionResponse> transactions(Long securityId) {
        List<TransactionRecord> records = (securityId == null)
                ? transactionRepository.findAll(Sort.by(Sort.Direction.DESC, "blockNumber"))
                : transactionRepository.findBySecurityIdOrderByBlockNumberDesc(securityId);
        return records.stream().map(TransactionResponse::from).toList();
    }
}
