package com.sto.platform.service;

import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.Security;
import com.sto.platform.domain.SecurityRepository;
import com.sto.platform.dto.SecurityCreateRequest;
import com.sto.platform.error.ConflictException;
import com.sto.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

/// 증권(종목) 등록·조회. 이미 배포된 컨트랙트 주소를 연결하고,
/// 체인에서 maxSupply를 읽어 검증한 뒤 보유량 미러를 초기화한다(명세: 온체인 정본).
@Service
public class SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);

    private final SecurityRepository securityRepository;
    private final ChainService chainService;

    public SecurityService(SecurityRepository securityRepository, ChainService chainService) {
        this.securityRepository = securityRepository;
        this.chainService = chainService;
    }

    @Transactional
    public Security register(SecurityCreateRequest request) {
        String address = request.contractAddress().toLowerCase();
        if (securityRepository.findByContractAddress(address).isPresent()) {
            throw new ConflictException("이미 등록된 컨트랙트 주소입니다: " + address);
        }

        // 온체인 정본과 대조: 요청한 maxSupply가 컨트랙트의 maxSupply와 일치하는지 검증
        BigInteger onChainMax = chainService.maxSupply(address);
        if (onChainMax.signum() == 0) {
            throw new IllegalArgumentException(
                    "해당 주소에서 유효한 SecurityToken을 찾을 수 없습니다: " + address);
        }
        if (!onChainMax.equals(request.maxSupply())) {
            throw new IllegalStateException(
                    "요청 maxSupply(%s)가 온체인 값(%s)과 다릅니다".formatted(request.maxSupply(), onChainMax));
        }

        Security security = new Security(request.name(), request.symbol(), onChainMax);
        security.setContractAddress(address);
        // 등록 시점 온체인 발행량을 미러로 반영(이후 동기화로 갱신)
        security.setTotalSupply(chainService.totalSupply(address));

        Security saved = securityRepository.save(security);
        log.info("증권 등록 완료 id={} symbol={} address={} maxSupply={}",
                saved.getId(), saved.getSymbol(), address, onChainMax);
        return saved;
    }

    @Transactional(readOnly = true)
    public Security get(Long id) {
        return securityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("증권을 찾을 수 없습니다: " + id));
    }
}
