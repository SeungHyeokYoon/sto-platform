package com.sto.platform.chain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// ChainService ↔ 실제 체인(Anvil) 통합 테스트.
/// 환경변수 STO_CHAIN_CONTRACT_ADDRESS / STO_CHAIN_AGENT_PRIVATE_KEY 가 있을 때만 실행.
/// 일반 빌드(CI)에서는 자동으로 건너뛴다.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STO_CHAIN_CONTRACT_ADDRESS", matches = "0x.+")
class ChainServiceIT {

    // Anvil 1번 계정 — 테스트 투자자
    private static final String INVESTOR = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

    @Autowired
    ChainService chainService;

    @Autowired
    ChainProperties properties;

    @Test
    void whitelist_mint_andRead() {
        String contract = properties.contractAddress();

        // 1) whitelist 등록(KYC)
        chainService.setWhitelist(contract, INVESTOR, true);
        assertThat(chainService.isWhitelisted(contract, INVESTOR)).isTrue();

        // 2) 발행 전후 잔고 델타 검증
        BigInteger before = chainService.balanceOf(contract, INVESTOR);
        chainService.mint(contract, INVESTOR, BigInteger.valueOf(100));
        BigInteger after = chainService.balanceOf(contract, INVESTOR);

        assertThat(after.subtract(before)).isEqualTo(BigInteger.valueOf(100));
    }
}
