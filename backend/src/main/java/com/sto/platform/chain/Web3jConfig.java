package com.sto.platform.chain;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

/// web3j 연결 빈 구성. 연결은 지연 평가이므로 노드가 꺼져 있어도 빈 생성은 성공한다.
@Configuration
@EnableConfigurationProperties(ChainProperties.class)
public class Web3jConfig {

    @Bean
    public Web3j web3j(ChainProperties properties) {
        return Web3j.build(new HttpService(properties.rpcUrl()));
    }

    /// 로컬 체인(Anvil/Besu)용 고정 가스 설정. 권한형 체인이라 기본값으로 충분.
    @Bean
    public ContractGasProvider contractGasProvider() {
        return new DefaultGasProvider();
    }
}
