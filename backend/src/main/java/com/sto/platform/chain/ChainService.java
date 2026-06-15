package com.sto.platform.chain;

import com.sto.platform.chain.contracts.SecurityToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;

/// 블록체인 연동(명세 6.3 ChainService).
/// 증권마다 컨트랙트 주소가 다르므로 주소를 받아 래퍼를 동적으로 load한다.
/// 서명 키는 실제 서명 시점에 지연 로딩한다(키 없이도 앱 구동·읽기 가능).
@Service
public class ChainService {

    private static final Logger log = LoggerFactory.getLogger(ChainService.class);

    private final Web3j web3j;
    private final ContractGasProvider gasProvider;
    private final ChainProperties properties;

    private volatile Credentials agentCredentials;

    public ChainService(Web3j web3j, ContractGasProvider gasProvider, ChainProperties properties) {
        this.web3j = web3j;
        this.gasProvider = gasProvider;
        this.properties = properties;
    }

    // ─── 쓰기(트랜잭션) ─────────────────────────────────────────────

    /// 투자자 whitelist(KYC) 등록/해제.
    public TransactionReceipt setWhitelist(String contractAddress, String investor, boolean status) {
        return send("setWhitelist",
                token(contractAddress).setWhitelist(investor, status));
    }

    /// 락업 해제 시각 설정.
    public TransactionReceipt setLockup(String contractAddress, String investor, BigInteger until) {
        return send("setLockup",
                token(contractAddress).setLockup(investor, until));
    }

    /// 발행(mint).
    public TransactionReceipt mint(String contractAddress, String to, BigInteger amount) {
        return send("mint", token(contractAddress).mint(to, amount));
    }

    /// 계좌관리기관 대행 이전(배정·강제이전). 동일 전송 제약 적용.
    public TransactionReceipt agentTransfer(String contractAddress, String from, String to, BigInteger amount) {
        return send("agentTransfer", token(contractAddress).agentTransfer(from, to, amount));
    }

    // ─── 읽기(조회) ─────────────────────────────────────────────────

    public BigInteger balanceOf(String contractAddress, String account) {
        return call("balanceOf", token(contractAddress).balanceOf(account));
    }

    public BigInteger totalSupply(String contractAddress) {
        return call("totalSupply", token(contractAddress).totalSupply());
    }

    public boolean isWhitelisted(String contractAddress, String account) {
        return call("whitelisted", token(contractAddress).whitelisted(account));
    }

    // ─── 내부 ───────────────────────────────────────────────────────

    private SecurityToken token(String contractAddress) {
        if (!StringUtils.hasText(contractAddress)) {
            throw new ChainException("컨트랙트 주소가 비어 있습니다");
        }
        return SecurityToken.load(contractAddress, web3j, credentials(), gasProvider);
    }

    private Credentials credentials() {
        Credentials c = agentCredentials;
        if (c == null) {
            synchronized (this) {
                c = agentCredentials;
                if (c == null) {
                    if (!StringUtils.hasText(properties.agentPrivateKey())) {
                        throw new ChainException(
                                "STO_CHAIN_AGENT_PRIVATE_KEY 환경변수가 설정되지 않았습니다");
                    }
                    c = Credentials.create(properties.agentPrivateKey());
                    agentCredentials = c;
                    log.info("agent 서명 계정 로드: {}", c.getAddress());
                }
            }
        }
        return c;
    }

    private <T> T send(String op, org.web3j.protocol.core.RemoteFunctionCall<T> fnCall) {
        try {
            return fnCall.send();
        } catch (Exception e) {
            throw new ChainException("온체인 트랜잭션 실패: " + op, e);
        }
    }

    private <T> T call(String op, org.web3j.protocol.core.RemoteFunctionCall<T> fnCall) {
        try {
            return fnCall.send();
        } catch (Exception e) {
            throw new ChainException("온체인 조회 실패: " + op, e);
        }
    }
}
