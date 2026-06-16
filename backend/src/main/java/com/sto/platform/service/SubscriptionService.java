package com.sto.platform.service;

import com.sto.platform.domain.*;
import com.sto.platform.dto.SubscriptionRequest;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 청약 신청(명세 ②). 자격확인(KYC) 후 신청을 PENDING으로 저장. 배정은 별도 단계.
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SecurityService securityService;
    private final InvestorRepository investorRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SecurityService securityService,
                               InvestorRepository investorRepository,
                               SubscriptionRepository subscriptionRepository) {
        this.securityService = securityService;
        this.investorRepository = investorRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public Subscription subscribe(SubscriptionRequest request) {
        securityService.get(request.securityId()); // 존재 확인
        Investor investor = investorRepository.findById(request.investorId())
                .orElseThrow(() -> new NotFoundException("투자자를 찾을 수 없습니다: " + request.investorId()));

        if (investor.getKycStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException("KYC 미승인 투자자는 청약할 수 없습니다");
        }

        Subscription saved = subscriptionRepository.save(
                new Subscription(request.securityId(), request.investorId(), request.amount()));
        log.info("청약 신청 id={} securityId={} investorId={} amount={}",
                saved.getId(), request.securityId(), request.investorId(), request.amount());
        return saved;
    }
}
