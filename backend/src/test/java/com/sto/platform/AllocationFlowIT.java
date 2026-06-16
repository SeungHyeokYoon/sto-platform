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

/// 청약·배정 전체 흐름 통합 테스트(실 체인 + 실 DB).
/// 투자자/증권 등록 → 투자자·창고 whitelist → 창고에 발행(가용 물량) → 청약 → 배정 → 온체인 잔고 확인.
/// STO_CHAIN_CONTRACT_ADDRESS(새로 배포한 깨끗한 컨트랙트)가 있을 때만 실행.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "STO_CHAIN_CONTRACT_ADDRESS", matches = "0x.+")
class AllocationFlowIT {

    private static final String INVESTOR = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"; // Anvil 1
    private static final BigInteger MAX = new BigInteger("1000000");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChainService chainService;

    @Test
    void subscribe_thenAllocate_fromTreasury_reflectsOnChain() throws Exception {
        String contract = System.getenv("STO_CHAIN_CONTRACT_ADDRESS");
        String treasury = chainService.agentAddress();

        Long invId = id("/api/investors", new InvestorCreateRequest("앨리스", INVESTOR));
        Long secId = id("/api/securities", new SecurityCreateRequest("KOFIA Bond", "KBND", MAX, contract));

        // 투자자 whitelist
        mockMvc.perform(post("/api/securities/{id}/whitelist", secId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WhitelistRequest(invId, true))))
                .andExpect(status().isOk());

        // 창고(treasury=agent) whitelist + 가용 물량 발행(설정 단계, 체인 직접 호출)
        chainService.setWhitelist(contract, treasury, true);
        chainService.mint(contract, treasury, new BigInteger("1000"));

        // 청약 500
        Long subId = id("/api/subscriptions", new SubscriptionRequest(secId, invId, new BigInteger("500")));

        // 배정 300 (창고 → 투자자)
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subId, new BigInteger("300")))))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("ALLOCATED"));

        // 온체인 반영 확인: 투자자 +300, 창고 1000 → 700
        assertThat(chainService.balanceOf(contract, INVESTOR)).isEqualTo(new BigInteger("300"));
        assertThat(chainService.balanceOf(contract, treasury)).isEqualTo(new BigInteger("700"));
    }

    private Long id(String path, Object body) throws Exception {
        MvcResult r = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
