package com.sto.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.chain.ChainService;
import com.sto.platform.domain.*;
import com.sto.platform.dto.TransferRequest;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// 권리이전 API 통합 테스트(실 DB + ChainService mock). 사전검증 분기를 검증.
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class TransferControllerTest {

    private static final String ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
    private static final String FROM_W = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String TO_W = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private static final BigInteger MAX = new BigInteger("1000000");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SecurityRepository securityRepository;
    @Autowired InvestorRepository investorRepository;

    @MockitoBean ChainService chainService;

    private Long securityId;
    private Long fromId;
    private Long toId;

    @BeforeEach
    void setup() {
        Security s = new Security("KOFIA Bond", "KBND", MAX);
        s.setContractAddress(ADDRESS);
        securityId = securityRepository.save(s).getId();
        fromId = investorRepository.save(new Investor("보내는이", FROM_W, KycStatus.APPROVED)).getId();
        toId = investorRepository.save(new Investor("받는이", TO_W, KycStatus.APPROVED)).getId();

        // 기본 stub: 양측 whitelist, 락업 없음
        given(chainService.isWhitelisted(anyString(), anyString())).willReturn(true);
        given(chainService.lockupUntil(anyString(), anyString())).willReturn(BigInteger.ZERO);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void transfer_success_returns201() throws Exception {
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("500"));
        TransactionReceipt receipt = mockReceipt();
        given(chainService.agentTransfer(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .willReturn(receipt);

        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(securityId, fromId, toId, new BigInteger("200")))))
                .andExpect(status().isCreated());
    }

    @Test
    void transfer_insufficientBalance_returns422() throws Exception {
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("100"));
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(securityId, fromId, toId, new BigInteger("200")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_receiverNotWhitelisted_returns422() throws Exception {
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("500"));
        given(chainService.isWhitelisted(anyString(), org.mockito.ArgumentMatchers.eq(TO_W))).willReturn(false);
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(securityId, fromId, toId, new BigInteger("200")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_lockedUp_returns422() throws Exception {
        given(chainService.balanceOf(anyString(), anyString())).willReturn(new BigInteger("500"));
        given(chainService.lockupUntil(anyString(), anyString()))
                .willReturn(BigInteger.valueOf(java.time.Instant.now().getEpochSecond() + 86400));
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(securityId, fromId, toId, new BigInteger("200")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(securityId, fromId, toId, new BigInteger("-5")))))
                .andExpect(status().isBadRequest());
    }

    private TransactionReceipt mockReceipt() {
        TransactionReceipt r = org.mockito.Mockito.mock(TransactionReceipt.class);
        given(r.isStatusOK()).willReturn(true);
        given(r.getTransactionHash()).willReturn("0xabc");
        given(r.getBlockNumber()).willReturn(BigInteger.ONE);
        return r;
    }
}
