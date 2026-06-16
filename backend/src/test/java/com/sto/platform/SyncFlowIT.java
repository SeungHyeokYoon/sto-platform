package com.sto.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sto.platform.domain.*;
import com.sto.platform.dto.*;
import com.sto.platform.sync.ReconcileService;
import com.sto.platform.sync.SyncService;
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

/// 3주차 핵심: 온체인→DB 동기화 + 멱등성 + 정합성 대사 통합 테스트(실 체인 + 실 DB).
/// STO_CHAIN_CONTRACT_ADDRESS(새로 배포한 깨끗한 컨트랙트)가 있을 때만 실행.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "STO_CHAIN_CONTRACT_ADDRESS", matches = "0x.+")
class SyncFlowIT {

    private static final String A = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String B = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private static final BigInteger MAX = new BigInteger("1000000");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SyncService syncService;
    @Autowired ReconcileService reconcileService;
    @Autowired HoldingRepository holdingRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void onChainOps_thenSync_populatesMirror_idempotent_andReconcileCorrects() throws Exception {
        String contract = System.getenv("STO_CHAIN_CONTRACT_ADDRESS");

        Long aId = id("/api/investors", new InvestorCreateRequest("앨리스", A));
        Long bId = id("/api/investors", new InvestorCreateRequest("밥", B));
        Long secId = id("/api/securities", new SecurityCreateRequest("KOFIA Bond", "KBND", MAX, contract));
        whitelist(secId, aId);
        whitelist(secId, bId);

        // 온체인 작업(API는 체인만 변경, DB 미러는 아직 비어있음)
        ok(post("/api/issuance"), new IssuanceRequest(secId, aId, new BigInteger("500")));
        ok(post("/api/transfers"), new TransferRequest(secId, aId, bId, new BigInteger("200")));

        // 동기화 전: 미러 비어있음
        assertThat(holdingRepository.findBySecurityId(secId)).isEmpty();

        // 동기화: 온체인 이벤트 → DB 반영
        int applied = syncService.syncSecurity(secId);
        assertThat(applied).isEqualTo(2); // Issued + Transferred

        BigInteger balA = holdingRepository.findById(new HoldingId(aId, secId)).orElseThrow().getBalance();
        BigInteger balB = holdingRepository.findById(new HoldingId(bId, secId)).orElseThrow().getBalance();
        assertThat(balA).isEqualTo(new BigInteger("300"));
        assertThat(balB).isEqualTo(new BigInteger("200"));
        assertThat(transactionRepository.findBySecurityIdOrderByBlockNumberDesc(secId)).hasSize(2);

        // 멱등성: 다시 동기화해도 신규 0, 잔고 불변
        assertThat(syncService.syncSecurity(secId)).isZero();
        assertThat(holdingRepository.findById(new HoldingId(aId, secId)).orElseThrow().getBalance())
                .isEqualTo(new BigInteger("300"));

        // 정합성 대사: DB를 일부러 오염시킨 뒤 대사 → 온체인 정본으로 복구
        Holding corrupt = holdingRepository.findById(new HoldingId(aId, secId)).orElseThrow();
        corrupt.setBalance(new BigInteger("999"));
        holdingRepository.saveAndFlush(corrupt);

        ReconcileReport report = reconcileService.reconcile(secId);
        assertThat(report.mismatches()).isGreaterThanOrEqualTo(1);
        assertThat(report.corrected()).isGreaterThanOrEqualTo(1);
        assertThat(holdingRepository.findById(new HoldingId(aId, secId)).orElseThrow().getBalance())
                .isEqualTo(new BigInteger("300")); // 정본으로 복구됨
    }

    private void whitelist(Long secId, Long investorId) throws Exception {
        mockMvc.perform(post("/api/securities/{id}/whitelist", secId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WhitelistRequest(investorId, true))))
                .andExpect(status().isOk());
    }

    private void ok(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                    Object body) throws Exception {
        mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
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
