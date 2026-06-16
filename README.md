# 토큰증권(STO) 발행·유통 프로토타입

증권사(계좌관리기관) 관점에서 토큰증권을 **발행·청약·배정·권리이전**하고, 그 결과를
**온체인 정본(전자등록계좌부)과 오프체인 권리자명부 DB 사이에 정합성 있게 유지**하는
백엔드 + 스마트컨트랙트 프로토타입이다.

> 전체 명세(단일 기준 문서)는 [STO_claude.md](STO_claude.md), 작업 지침은 [CLAUDE.md](CLAUDE.md),
> 아키텍처·법제 매핑·트레이드오프는 [docs/architecture.md](docs/architecture.md) 참조.

## 무엇을 보여주는가

- **온/오프체인 정합성**: 온체인이 정본, DB는 미러. 이벤트 기반 단방향 동기화 + 주기 대사·복구.
- **전송 제약의 코드 강제**: KYC(whitelist)·락업·발행한도를 컨트랙트 `require`로 최종 강제(컴플라이언스 이중화).
- **장애 처리**: checkpoint 기반 누락 복구 + 멱등(`tx_hash` UNIQUE) + 정합성 대사 자동 복구.

## 기술 스택

| 계층 | 기술 |
|---|---|
| 스마트컨트랙트 | Solidity 0.8.28 + Foundry(forge/anvil) |
| 백엔드 | Java 21 / Spring Boot 3.5 + web3j + JPA |
| 데이터 | PostgreSQL 16 |
| 인프라 | Docker / docker-compose |

## 디렉토리 구조

```
sto-platform/
├─ contracts/                 # Foundry: SecurityToken.sol + 테스트 + 배포 스크립트
├─ backend/                   # Spring Boot 백엔드
│  └─ src/main/java/com/sto/platform/
│     ├─ chain/               # web3j 연동(ChainService) + 생성 래퍼
│     ├─ controller/          # REST API
│     ├─ service/             # 업무 흐름 + 컴플라이언스 사전검증
│     ├─ sync/                # 이벤트 동기화 + 정합성 대사
│     ├─ domain/              # JPA 엔티티/리포지토리
│     ├─ dto/                 # 요청/응답
│     └─ error/               # 전역 예외 처리
├─ docker-compose.yml         # postgres + (통합 데모) backend
├─ demo.sh                    # 통합 데모 스크립트
└─ docs/                      # 아키텍처·법제·트레이드오프
```

## 실행

### 사전 도구
- JDK 21, Foundry(forge/anvil), Docker Desktop, Git Bash

### A. 통합 데모 (한 번에)
```bash
./demo.sh
```
Anvil 기동 → 컨트랙트 배포 → 백엔드 jar 빌드 → `docker compose up`(postgres+backend) →
발행→청약→배정→이전→자동 동기화→정합성 대사를 REST로 시연한다.

### B. 개발 모드 (수동)
```bash
# 1) 로컬 체인
anvil

# 2) 컨트랙트 컴파일·테스트·배포
cd contracts && forge test
forge create src/SecurityToken.sol:SecurityToken \
  --rpc-url http://127.0.0.1:8545 \
  --private-key 0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80 \
  --broadcast --constructor-args "KOFIA Bond" "KBND" 1000000

# 3) DB
docker compose up -d postgres

# 4) 백엔드 (환경변수 주입 후 실행)
cd backend
export STO_CHAIN_AGENT_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
./gradlew bootRun
```

### 테스트
```bash
cd backend && ./gradlew test          # 단위·통합(실체인 IT는 자동 skip)
cd contracts && forge test            # 컨트랙트 테스트
```
실체인 통합 테스트(`*FlowIT`, `*IT`)는 `STO_CHAIN_CONTRACT_ADDRESS` 환경변수가 있을 때만 실행된다.

## REST API (명세 6.1)

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/investors` | 투자자 등록(+KYC mock) |
| GET | `/api/investors/{id}/holdings` | 투자자 보유 잔고 |
| POST | `/api/securities` | 증권 등록(배포된 컨트랙트 연결 + 온체인 검증) |
| POST | `/api/securities/{id}/whitelist` | 증권별 whitelist(거래 허가) |
| GET | `/api/securities/{id}/holders` | 권리자명부 |
| POST | `/api/issuance` | 발행(mint) |
| POST | `/api/subscriptions` | 청약 신청 |
| POST | `/api/allocations` | 배정(창고→투자자) |
| POST | `/api/transfers` | 권리이전 |
| GET | `/api/transactions` | 거래내역 |
| POST | `/api/reconciliation/run` | 수동 정합성 대사 |

자세한 설계 근거와 트레이드오프는 [docs/architecture.md](docs/architecture.md).
