package com.sto.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.chain.ChainService;
import com.sto.platform.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// 전체 발행 흐름 통합 테스트(실 체인 + 실 DB).
/// 투자자 등록 → 증권 등록 → 증권별 whitelist → 발행 → 온체인 잔고 확인.
/// STO_CHAIN_CONTRACT_ADDRESS(새로 배포한 컨트랙트 주소)가 있을 때만 실행된다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "STO_CHAIN_CONTRACT_ADDRESS", matches = "0x.+")
class IssuanceFlowIT {

    private static final String WALLET = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"; // Anvil 1번
    private static final BigInteger MAX = new BigInteger("1000000");
    private static final BigInteger AMOUNT = new BigInteger("500");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ChainService chainService;

    @Test
    void registerInvestor_registerSecurity_whitelist_issue_reflectsOnChain() throws Exception {
        String contractAddress = System.getenv("STO_CHAIN_CONTRACT_ADDRESS");

        // 1) 투자자 등록
        Long investorId = idOf(mockMvc.perform(post("/api/investors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new InvestorCreateRequest("홍길동", WALLET))))
                .andExpect(status().isCreated()).andReturn());

        // 2) 증권 등록(배포된 컨트랙트 연결)
        Long securityId = idOf(mockMvc.perform(post("/api/securities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SecurityCreateRequest("KOFIA Bond", "KBND", MAX, contractAddress))))
                .andExpect(status().isCreated()).andReturn());

        // 3) 증권별 whitelist 등록
        mockMvc.perform(post("/api/securities/{id}/whitelist", securityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WhitelistRequest(investorId, true))))
                .andExpect(status().isOk());

        // 4) 발행
        BigInteger before = chainService.balanceOf(contractAddress, WALLET);
        mockMvc.perform(post("/api/issuance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new IssuanceRequest(securityId, investorId, AMOUNT))))
                .andExpect(status().isCreated());

        // 5) 온체인 잔고가 실제로 증가했는지 확인
        BigInteger after = chainService.balanceOf(contractAddress, WALLET);
        assertThat(after.subtract(before)).isEqualTo(AMOUNT);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private Long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
