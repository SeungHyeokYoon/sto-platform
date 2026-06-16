package com.sto.platform.service;

import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.*;
import com.sto.platform.dto.AllocationRequest;
import com.sto.platform.dto.AllocationResponse;
import com.sto.platform.error.ConflictException;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

/// 배정(명세 ②). 청약 건에 배정량을 결정하고, 창고(treasury=agent 계정)에서
/// 투자자에게 agentTransfer로 이전한다. 배정량 ≤ 신청량 && ≤ 가용 물량(창고 잔고).
/// DB 명부(holding)는 3주차 이벤트 동기화로 반영(온체인→DB 단방향).
@Service
public class AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SecurityService securityService;
    private final InvestorRepository investorRepository;
    private final ChainService chainService;

    public AllocationService(SubscriptionRepository subscriptionRepository,
                             SecurityService securityService,
                             InvestorRepository investorRepository,
                             ChainService chainService) {
        this.subscriptionRepository = subscriptionRepository;
        this.securityService = securityService;
        this.investorRepository = investorRepository;
        this.chainService = chainService;
    }

    @Transactional
    public AllocationResponse allocate(AllocationRequest request) {
        Subscription subscription = subscriptionRepository.findById(request.subscriptionId())
                .orElseThrow(() -> new NotFoundException("청약을 찾을 수 없습니다: " + request.subscriptionId()));
        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new ConflictException("이미 처리된 청약입니다: " + subscription.getStatus());
        }

        Security security = securityService.get(subscription.getSecurityId());
        Investor investor = investorRepository.findById(subscription.getInvestorId())
                .orElseThrow(() -> new NotFoundException("투자자를 찾을 수 없습니다: " + subscription.getInvestorId()));

        String contract = security.getContractAddress();
        String treasury = chainService.agentAddress();
        String wallet = investor.getWalletAddress();
        BigInteger amount = request.amount();

        // 컴플라이언스/업무 사전검증
        if (amount.compareTo(subscription.getRequestedAmount()) > 0) {
            throw new IllegalStateException("배정량이 신청량을 초과합니다");
        }
        if (investor.getKycStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException("투자자 KYC가 승인되지 않았습니다");
        }
        if (!chainService.isWhitelisted(contract, wallet)) {
            throw new IllegalStateException("투자자가 whitelist되어 있지 않습니다");
        }
        BigInteger available = chainService.balanceOf(contract, treasury);
        if (amount.compareTo(available) > 0) {
            throw new IllegalStateException("가용 물량(창고 잔고)을 초과합니다");
        }

        // 창고 → 투자자 대행 이전
        TransactionReceipt receipt = chainService.agentTransfer(contract, treasury, wallet, amount);

        subscription.setAllocatedAmount(amount);
        subscription.setStatus(SubscriptionStatus.ALLOCATED);
        log.info("배정 subscriptionId={} securityId={} investorId={} amount={} tx={}",
                subscription.getId(), security.getId(), investor.getId(), amount, receipt.getTransactionHash());

        return new AllocationResponse(subscription.getId(), security.getId(), investor.getId(),
                wallet, amount, subscription.getStatus(),
                receipt.getTransactionHash(), receipt.getBlockNumber());
    }
}
