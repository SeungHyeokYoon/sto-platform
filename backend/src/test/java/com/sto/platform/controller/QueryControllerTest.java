package com.sto.platform.controller;

import com.sto.platform.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// 조회 API 통합 테스트(실 DB에 미러 행 직접 시드).
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class QueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired SecurityRepository securityRepository;
    @Autowired InvestorRepository investorRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired TransactionRepository transactionRepository;

    private Long securityId;
    private Long holderId;
    private Long zeroId;

    @BeforeEach
    void seed() {
        Security s = new Security("KOFIA Bond", "KBND", new BigInteger("1000000"));
        s.setContractAddress("0x5fbdb2315678afecb367f032d93f642f64180aa3");
        securityId = securityRepository.save(s).getId();

        holderId = investorRepository.save(
                new Investor("앨리스", "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", KycStatus.APPROVED)).getId();
        zeroId = investorRepository.save(
                new Investor("빈손이", "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc", KycStatus.APPROVED)).getId();

        holdingRepository.save(new Holding(holderId, securityId, new BigInteger("300")));
        holdingRepository.save(new Holding(zeroId, securityId, BigInteger.ZERO)); // 0 보유 → 명부 제외

        transactionRepository.save(new TransactionRecord(
                securityId, "0xhash1", 10L, TxType.ISSUE,
                null, "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                new BigInteger("300"), TxStatus.CONFIRMED));
    }

    @Test
    void holders_excludesZeroBalance() throws Exception {
        mockMvc.perform(get("/api/securities/{id}/holders", securityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].investorId").value(holderId))
                .andExpect(jsonPath("$[0].balance").value(300));
    }

    @Test
    void holders_unknownSecurity_returns404() throws Exception {
        mockMvc.perform(get("/api/securities/{id}/holders", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void holdings_returnsInvestorPositions() throws Exception {
        mockMvc.perform(get("/api/investors/{id}/holdings", holderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].securityId").value(securityId))
                .andExpect(jsonPath("$[0].symbol").value("KBND"))
                .andExpect(jsonPath("$[0].balance").value(300));
    }

    @Test
    void transactions_filteredBySecurity() throws Exception {
        mockMvc.perform(get("/api/transactions").param("securityId", securityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].txHash").value("0xhash1"))
                .andExpect(jsonPath("$[0].type").value("ISSUE"));
    }
}
