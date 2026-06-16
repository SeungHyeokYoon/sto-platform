package com.sto.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.domain.*;
import com.sto.platform.dto.SubscriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// 청약 신청 API 통합 테스트(실 DB). 청약은 체인을 건드리지 않는다.
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class SubscriptionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SecurityRepository securityRepository;
    @Autowired InvestorRepository investorRepository;

    private Long securityId;
    private Long approvedId;
    private Long pendingId;

    @BeforeEach
    void seed() {
        Security s = new Security("KOFIA Bond", "KBND", new BigInteger("1000000"));
        s.setContractAddress("0x5fbdb2315678afecb367f032d93f642f64180aa3");
        securityId = securityRepository.save(s).getId();
        approvedId = investorRepository.save(
                new Investor("앨리스", "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", KycStatus.APPROVED)).getId();
        pendingId = investorRepository.save(
                new Investor("미승인", "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc", KycStatus.PENDING)).getId();
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void subscribe_returns201_pending() throws Exception {
        mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubscriptionRequest(securityId, approvedId, new BigInteger("500")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAmount").value(500));
    }

    @Test
    void subscribe_kycNotApproved_returns422() throws Exception {
        mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubscriptionRequest(securityId, pendingId, new BigInteger("500")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void subscribe_unknownSecurity_returns404() throws Exception {
        mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubscriptionRequest(999999L, approvedId, new BigInteger("500")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribe_invalidAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubscriptionRequest(securityId, approvedId, new BigInteger("0")))))
                .andExpect(status().isBadRequest());
    }
}
