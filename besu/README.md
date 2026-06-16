# Besu QBFT 멀티노드 ("여러 기관 합의" 시연)

검증자 노드 4개로 구성된 Hyperledger Besu **QBFT**(권한형 BFT) 네트워크다.
여러 노드(기관)가 **번갈아 블록을 제안하고 합의**하는 구조를 로컬에서 재현한다.
개발/테스트는 Anvil(단일 노드)을 쓰고, 이 네트워크는 멀티노드 합의를 보여줄 때만 띄운다.

> ⚠️ **`networkFiles/`, `nodes/` 의 키는 로컬 시연 전용 throwaway 검증자 키다.**
> 실제 네트워크/자산에 절대 재사용하지 말 것. agent 가스 계정도 잘 알려진 Anvil 0번 키다.

## 구성
- **검증자 4개** (BFT: n≥3f+1 → 1개 장애까지 견딤)
- **QBFT**, 블록 주기 **2초**, chainId **1337**
- 포크는 **Berlin**(EIP-1559 없음 → legacy 가스). web3j/forge 모두 legacy 거래로 호환.
- genesis에서 agent 계정(`0xf39Fd6…`)에 가스용 잔고 지급.
- besu는 enode에 IP를 요구하므로 **고정 IP 네트워크**(172.28.0.11~14) + 각 노드 `--p2p-host` 사용.

## 실행
```bash
# 1) 네트워크 기동 (검증자 4개)
docker compose -f docker-compose.besu.yml up -d

# 2) 합의 확인 (블록 증가 + 제안자 회전)
cast block-number --rpc-url http://127.0.0.1:8545
cast block <n> --rpc-url http://127.0.0.1:8545 --json | grep miner   # 블록마다 제안자가 바뀜

# 3) 컨트랙트 배포 (Berlin 체인이라 --legacy 필수)
cd contracts
forge create src/SecurityToken.sol:SecurityToken \
  --rpc-url http://127.0.0.1:8545 \
  --private-key 0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80 \
  --broadcast --legacy --constructor-args "KOFIA Bond" "KBND" 1000000

# 4) 백엔드를 이 RPC로 연결 (코드 변경 없이 RPC 주소만 교체)
export STO_CHAIN_RPC_URL=http://127.0.0.1:8545
export STO_CHAIN_AGENT_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
export STO_CHAIN_CONTRACT_ADDRESS=<배포주소>
# ./gradlew bootRun  또는 통합테스트로 검증:
# ./gradlew test --tests "com.sto.platform.chain.ChainServiceIT"

# 5) 정리
docker compose -f docker-compose.besu.yml down
```

## 네트워크 파일 재생성 (선택)
키/genesis는 `qbftConfigFile.json` 기준으로 besu operator 도구로 생성한다:
```bash
# Git Bash 기준. MSYS 경로 변환 방지 필요.
BESU_DIR="$(cd besu && pwd -W)"
rm -rf besu/networkFiles
MSYS_NO_PATHCONV=1 docker run --rm -v "${BESU_DIR}:/data" hyperledger/besu:25.12.0 \
  operator generate-blockchain-config \
  --config-file=/data/qbftConfigFile.json --to=/data/networkFiles --private-key-file-name=key
# 생성된 keys/<주소>/ 를 nodes/besu1..4 로 재배치하고,
# besu1 의 key.pub(0x 제거)로 docker-compose.besu.yml 의 부트노드 enode를 갱신한다.
```
> 재생성하면 노드 주소·genesis 검증자·besu1 enode가 모두 바뀌므로,
> `docker-compose.besu.yml` 의 `--bootnodes` enode도 새 besu1 pubkey로 바꿔야 한다.

## 핵심 포인트
- **백엔드 코드·컨트랙트는 그대로**다. Anvil → Besu 전환은 **RPC 주소만** 바꾸면 된다.
- 블록 생성(합의)을 여러 노드가 공동 수행 ≠ 발행 권한 공유. 발행 권한은 여전히 단일 `agent`(`onlyAgent`).
  여러 기관이 발행 권한까지 나누려면 컨트랙트 권한 모델(멀티 agent/멀티시그)을 별도로 바꿔야 한다.
