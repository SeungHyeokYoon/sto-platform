package com.sto.platform.service;

import com.sto.platform.domain.Investor;
import com.sto.platform.domain.InvestorRepository;
import com.sto.platform.domain.KycStatus;
import com.sto.platform.dto.InvestorCreateRequest;
import com.sto.platform.error.ConflictException;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 투자자 등록·조회. 등록은 DB + KYC(mock)까지만 처리하고,
/// 온체인 whitelist는 증권별로 별도 단계에서 건다(설계 결정).
@Service
public class InvestorService {

    private static final Logger log = LoggerFactory.getLogger(InvestorService.class);

    private final InvestorRepository investorRepository;

    public InvestorService(InvestorRepository investorRepository) {
        this.investorRepository = investorRepository;
    }

    @Transactional
    public Investor register(InvestorCreateRequest request) {
        String wallet = request.walletAddress().toLowerCase();
        if (investorRepository.existsByWalletAddress(wallet)) {
            throw new ConflictException("이미 등록된 지갑 주소입니다: " + wallet);
        }
        // mock KYC: 본 프로토타입은 실 사업자 연동 대신 자동 승인 처리
        Investor investor = new Investor(request.name(), wallet, KycStatus.APPROVED);
        Investor saved = investorRepository.save(investor);
        log.info("투자자 등록 완료 id={} wallet={} kyc={}", saved.getId(), wallet, saved.getKycStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public Investor get(Long id) {
        return investorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("투자자를 찾을 수 없습니다: " + id));
    }
}
