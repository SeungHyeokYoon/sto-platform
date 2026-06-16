package com.sto.platform.service;

import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.Investor;
import com.sto.platform.domain.InvestorRepository;
import com.sto.platform.domain.KycStatus;
import com.sto.platform.domain.Security;
import com.sto.platform.dto.TransferRequest;
import com.sto.platform.dto.TransferResponse;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;

/// 권리이전(명세 ③). 컴플라이언스 사전검증 후 계좌관리기관 대행 이전(agentTransfer)을 수행한다.
/// 백엔드는 agent로 서명하므로 투자자 본인 서명형 transfer가 아닌 agentTransfer를 사용.
/// DB 명부는 3주차 이벤트 동기화로 반영(온체인→DB 단방향).
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final SecurityService securityService;
    private final InvestorRepository investorRepository;
    private final ChainService chainService;

    public TransferService(SecurityService securityService,
                           InvestorRepository investorRepository,
                           ChainService chainService) {
        this.securityService = securityService;
        this.investorRepository = investorRepository;
        this.chainService = chainService;
    }

    @Transactional(readOnly = true)
    public TransferResponse transfer(TransferRequest request) {
        Security security = securityService.get(request.securityId());
        Investor from = investor(request.fromInvestorId());
        Investor to = investor(request.toInvestorId());

        String address = security.getContractAddress();
        String fromWallet = from.getWalletAddress();
        String toWallet = to.getWalletAddress();
        BigInteger amount = request.amount();

        // 컴플라이언스 사전검증(빠른 실패용). 최종 강제는 컨트랙트 require가 담당.
        requireApproved(from, "보내는");
        requireApproved(to, "받는");
        if (!chainService.isWhitelisted(address, fromWallet)) {
            throw new IllegalStateException("보내는 투자자가 whitelist되어 있지 않습니다");
        }
        if (!chainService.isWhitelisted(address, toWallet)) {
            throw new IllegalStateException("받는 투자자가 whitelist되어 있지 않습니다");
        }
        BigInteger lockupUntil = chainService.lockupUntil(address, fromWallet);
        if (lockupUntil.compareTo(BigInteger.valueOf(Instant.now().getEpochSecond())) > 0) {
            throw new IllegalStateException("보내는 투자자의 락업이 해제되지 않았습니다");
        }
        BigInteger balance = chainService.balanceOf(address, fromWallet);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("보내는 투자자의 잔고가 부족합니다");
        }

        // 온체인 대행 이전
        TransactionReceipt receipt = chainService.agentTransfer(address, fromWallet, toWallet, amount);
        String status = receipt.isStatusOK() ? "CONFIRMED" : "FAILED";
        log.info("권리이전 securityId={} from={} to={} amount={} tx={} status={}",
                security.getId(), from.getId(), to.getId(), amount, receipt.getTransactionHash(), status);

        return new TransferResponse(security.getId(), from.getId(), to.getId(), amount,
                receipt.getTransactionHash(), receipt.getBlockNumber(), status);
    }

    private Investor investor(Long id) {
        return investorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("투자자를 찾을 수 없습니다: " + id));
    }

    private void requireApproved(Investor investor, String role) {
        if (investor.getKycStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException(role + " 투자자의 KYC가 승인되지 않았습니다");
        }
    }
}
