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

/// 권리이전 전체 흐름 통합 테스트(실 체인 + 실 DB).
/// A·B 등록 → 증권 등록 → 양측 whitelist → A에게 발행 → A→B 이전 → 온체인 잔고 확인.
/// STO_CHAIN_CONTRACT_ADDRESS(새로 배포한 깨끗한 컨트랙트)가 있을 때만 실행.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "STO_CHAIN_CONTRACT_ADDRESS", matches = "0x.+")
class TransferFlowIT {

    private static final String A = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"; // Anvil 1
    private static final String B = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"; // Anvil 2
    private static final BigInteger MAX = new BigInteger("1000000");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChainService chainService;

    @Test
    void issueToA_thenTransferAtoB_reflectsOnChain() throws Exception {
        String contract = System.getenv("STO_CHAIN_CONTRACT_ADDRESS");

        Long aId = id("/api/investors", new InvestorCreateRequest("앨리스", A));
        Long bId = id("/api/investors", new InvestorCreateRequest("밥", B));
        Long secId = id("/api/securities",
                new SecurityCreateRequest("KOFIA Bond", "KBND", MAX, contract));

        whitelist(secId, aId);
        whitelist(secId, bId);

        // A에게 500 발행
        mockMvc.perform(post("/api/issuance").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new IssuanceRequest(secId, aId, new BigInteger("500")))))
                .andExpect(status().isCreated());

        // A → B 200 이전
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(secId, aId, bId, new BigInteger("200")))))
                .andExpect(status().isCreated());

        assertThat(chainService.balanceOf(contract, A)).isEqualTo(new BigInteger("300"));
        assertThat(chainService.balanceOf(contract, B)).isEqualTo(new BigInteger("200"));
    }

    private void whitelist(Long secId, Long investorId) throws Exception {
        mockMvc.perform(post("/api/securities/{id}/whitelist", secId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WhitelistRequest(investorId, true))))
                .andExpect(status().isOk());
    }

    private Long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long id(String path, Object body) throws Exception {
        return id(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn());
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
