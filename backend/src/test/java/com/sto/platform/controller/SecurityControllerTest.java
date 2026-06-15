package com.sto.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.chain.ChainService;
import com.sto.platform.dto.SecurityCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// 증권 등록 API 통합 테스트(실 DB + ChainService mock).
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class SecurityControllerTest {

    private static final String ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
    private static final BigInteger MAX = new BigInteger("1000000");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ChainService chainService;

    @BeforeEach
    void stubChain() {
        given(chainService.maxSupply(anyString())).willReturn(MAX);
        given(chainService.totalSupply(anyString())).willReturn(BigInteger.ZERO);
    }

    private String body(String name, String symbol, BigInteger max, String address) throws Exception {
        return objectMapper.writeValueAsString(new SecurityCreateRequest(name, symbol, max, address));
    }

    @Test
    void register_returns201_andMirrorsOnChainSupply() throws Exception {
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("KOFIA Bond", "KBND", MAX, ADDRESS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.symbol").value("KBND"))
                .andExpect(jsonPath("$.maxSupply").value(1000000))
                .andExpect(jsonPath("$.totalSupply").value(0))
                .andExpect(jsonPath("$.contractAddress").value(ADDRESS.toLowerCase()));
    }

    @Test
    void register_maxSupplyMismatch_returns422() throws Exception {
        // 요청 maxSupply가 온체인 값과 다름
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("X", "X", new BigInteger("999"), ADDRESS)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_noContractAtAddress_returns400() throws Exception {
        // 해당 주소에 컨트랙트 없음(maxSupply 0)
        given(chainService.maxSupply(anyString())).willReturn(BigInteger.ZERO);
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("X", "X", MAX, ADDRESS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidAddress_returns400() throws Exception {
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("X", "X", MAX, "0xnope")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateAddress_returns409() throws Exception {
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("KOFIA Bond", "KBND", MAX, ADDRESS)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/securities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("KOFIA Bond", "KBND", MAX, ADDRESS)))
                .andExpect(status().isConflict());
    }
}
