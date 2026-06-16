package com.sto.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.*;
import com.sto.platform.dto.AllocationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// 배정 API 통합 테스트(실 DB + ChainService mock). 사전검증 분기와 상태전이를 검증.
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class AllocationControllerTest {

    private static final String TREASURY = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SecurityRepository securityRepository;
    @Autowired InvestorRepository investorRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    @MockitoBean ChainService chainService;

    private Long subscriptionId;

    @BeforeEach
    void seed() {
        Security s = new Security("KOFIA Bond", "KBND", new BigInteger("1000000"));
        s.setContractAddress("0x5fbdb2315678afecb367f032d93f642f64180aa3");
        Long securityId = securityRepository.save(s).getId();
        Long investorId = investorRepository.save(
                new Investor("앨리스", "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", KycStatus.APPROVED)).getId();
        subscriptionId = subscriptionRepository.save(
                new Subscription(securityId, investorId, new BigInteger("500"))).getId();

        TransactionReceipt receipt = mockReceipt();
        given(chainService.agentAddress()).willReturn(TREASURY);
        given(chainService.isWhitelisted(anyString(), anyString())).willReturn(true);
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("1000"));
        given(chainService.agentTransfer(anyString(), anyString(), anyString(), any())).willReturn(receipt);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void allocate_success_marksAllocated() throws Exception {
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subscriptionId, new BigInteger("300")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.allocatedAmount").value(300));
    }

    @Test
    void allocate_exceedsRequested_returns422() throws Exception {
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subscriptionId, new BigInteger("600")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void allocate_exceedsAvailable_returns422() throws Exception {
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("100"));
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subscriptionId, new BigInteger("300")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void allocate_alreadyAllocated_returns409() throws Exception {
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subscriptionId, new BigInteger("300")))))
                .andExpect(status().isCreated());
        // 같은 청약 재배정 시도
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AllocationRequest(subscriptionId, new BigInteger("100")))))
                .andExpect(status().isConflict());
    }

    private TransactionReceipt mockReceipt() {
        TransactionReceipt r = org.mockito.Mockito.mock(TransactionReceipt.class);
        given(r.getTransactionHash()).willReturn("0xabc");
        given(r.getBlockNumber()).willReturn(BigInteger.ONE);
        return r;
    }
}
