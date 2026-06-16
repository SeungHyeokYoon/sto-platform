package com.sto.platform.service;

import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.Investor;
import com.sto.platform.domain.InvestorRepository;
import com.sto.platform.domain.KycStatus;
import com.sto.platform.domain.Security;
import com.sto.platform.dto.IssuanceRequest;
import com.sto.platform.dto.IssuanceResponse;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

/// 발행(명세 ①). 컴플라이언스 사전검증 후 온체인 mint만 수행한다.
/// DB 명부(holding/transaction/total_supply)는 3주차 이벤트 동기화로 반영(온체인→DB 단방향).
@Service
public class IssuanceService {

    private static final Logger log = LoggerFactory.getLogger(IssuanceService.class);

    private final SecurityService securityService;
    private final InvestorRepository investorRepository;
    private final ChainService chainService;

    public IssuanceService(SecurityService securityService,
                           InvestorRepository investorRepository,
                           ChainService chainService) {
        this.securityService = securityService;
        this.investorRepository = investorRepository;
        this.chainService = chainService;
    }

    @Transactional(readOnly = true)
    public IssuanceResponse issue(IssuanceRequest request) {
        Security security = securityService.get(request.securityId());
        Investor investor = investorRepository.findById(request.investorId())
                .orElseThrow(() -> new NotFoundException("투자자를 찾을 수 없습니다: " + request.investorId()));

        String address = security.getContractAddress();
        String wallet = investor.getWalletAddress();
        BigInteger amount = request.amount();

        // 컴플라이언스 사전검증(빠른 실패용). 최종 강제는 컨트랙트 require가 담당.
        if (investor.getKycStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException("수령자 KYC가 승인되지 않았습니다");
        }
        if (!chainService.isWhitelisted(address, wallet)) {
            throw new IllegalStateException("수령자가 해당 증권에 whitelist되어 있지 않습니다");
        }
        BigInteger total = chainService.totalSupply(address);
        if (total.add(amount).compareTo(security.getMaxSupply()) > 0) {
            throw new IllegalStateException("발행한도(maxSupply)를 초과합니다");
        }

        // 온체인 발행
        TransactionReceipt receipt = chainService.mint(address, wallet, amount);
        String status = receipt.isStatusOK() ? "CONFIRMED" : "FAILED";
        log.info("발행 securityId={} investorId={} amount={} tx={} status={}",
                security.getId(), investor.getId(), amount, receipt.getTransactionHash(), status);

        return new IssuanceResponse(security.getId(), investor.getId(), wallet, amount,
                receipt.getTransactionHash(), receipt.getBlockNumber(), status);
    }
}
