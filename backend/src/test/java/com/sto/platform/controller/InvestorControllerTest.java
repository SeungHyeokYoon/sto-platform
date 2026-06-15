package com.sto.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.dto.InvestorCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// 투자자 등록 API 통합 테스트(실 DB). 체인 빈은 사용하지 않으므로 Web3j는 mock 처리.
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class InvestorControllerTest {

    private static final String WALLET = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 등록 흐름은 체인을 건드리지 않지만, 컨텍스트의 Web3j 빈 생성을 가볍게 대체
    @MockitoBean
    Web3j web3j;

    @Test
    void register_returns201_withApprovedKyc() throws Exception {
        String json = objectMapper.writeValueAsString(new InvestorCreateRequest("홍길동", WALLET));

        mockMvc.perform(post("/api/investors")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
                .andExpect(jsonPath("$.walletAddress").value(WALLET.toLowerCase()));
    }

    @Test
    void register_duplicateWallet_returns409() throws Exception {
        String json = objectMapper.writeValueAsString(new InvestorCreateRequest("홍길동", WALLET));
        mockMvc.perform(post("/api/investors").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/investors").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidWallet_returns400() throws Exception {
        String json = objectMapper.writeValueAsString(new InvestorCreateRequest("홍길동", "not-an-address"));
        mockMvc.perform(post("/api/investors").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/investors/{id}", 999999))
                .andExpect(status().isNotFound());
    }
}
