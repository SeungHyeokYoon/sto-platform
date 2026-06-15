package com.sto.platform.chain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// 체인 연동 설정(sto.chain.*). 값은 환경변수로 주입(.env.example 참조).
@ConfigurationProperties(prefix = "sto.chain")
public record ChainProperties(
        String rpcUrl,
        String agentPrivateKey,
        String contractAddress
) {
}
