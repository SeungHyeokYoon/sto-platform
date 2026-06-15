# 토큰증권(STO) 발행·유통 프로토타입 — 빌드 명세서 (로컬 전용)

> 이 문서는 **Claude Code로 구현을 진행하기 위한 단일 기준 문서(source of truth)** 다.
> 배포는 하지 않으며, 모든 구성요소는 로컬 PC 한 대에서 동작한다.
> 각 설계 결정에는 근거(타당성)를 함께 적었다.

---

## 1. 프로젝트 개요

### 1.1 한 줄 정의
증권사(계좌관리기관) 자리에서, 토큰증권을 **발행·청약·배정·권리이전**하고 그 결과를 **권리자명부 DB와 정합성 있게 유지**하는 백엔드 + 스마트컨트랙트 프로토타입.

### 1.2 배경
2026년 1월 전자증권법·자본시장법 개정으로 분산원장이 법적 전자등록계좌부로 인정되고(시행 예정 2027년 1월), 증권사가 계좌관리기관/유통 인프라를 직접 운영하게 된다. 본 프로젝트는 그 구조를 로컬에서 축소 재현한다.

### 1.3 관점
**증권사(계좌관리기관) 시점**에서 구현한다. 발행인의 요청과 전자등록기관의 승인은 mock으로 시뮬레이션한다.

### 1.4 목적
금융권/IT 면접 포트폴리오. 평가 포인트는 화려함이 아니라 **온/오프체인 정합성, 전송 제약의 코드 강제, 장애 상황 처리**다.

---

## 2. 구현 범위

### 2.1 핵심 기능

| # | 기능 | 핵심 내용 | 핵심 규칙 |
|---|---|---|---|
| ① | 발행(Issuance) | 발행인 요청 → 전자등록(mock 승인) → 컨트랙트 mint | 발행한도(maxSupply) 초과 불가, 수령자 KYC 필수 |
| ② | 청약·배정(Subscription/Allocation) | 투자자 신청 → 자격확인 → 배정량 결정 → 투자자에게 이전 | KYC 통과자만, 배정량 ≤ 가용 물량 |
| ③ | 권리이전(Transfer) | KYC 투자자 간 이전 | whitelist 양측 통과 + 락업 미경과 거부 |
| ④ | 명부 동기화(Sync) | 온체인 이벤트 → 오프체인 DB 반영 | 실시간, 온체인→DB 단방향, 멱등 |
| ⑤ | 정합성 검증(Reconciliation) | 온체인 잔고 vs DB 대사 | 주기적 배치, 불일치 검출·복구 |

### 2.2 범위 밖 (Out of Scope) — 의도적 제외
- 실제 KYC/AML 사업자 연동 (mock으로 대체)
- 메인넷/외부 네트워크 배포 (로컬 Anvil + 로컬 Besu만)
- 고가용성(HA), 다중 인스턴스, 멀티 기관 실배포
- 실 장외거래소·실명확인기관 연동
- **Kafka/Redis** (3.3 근거 참조)

---

## 3. 기술 스택

| 계층 | 기술 | 선택 이유 |
|---|---|---|
| 스마트컨트랙트 | **Solidity** (^0.8.x) | EVM 표준 컨트랙트 언어. 전송 제약을 코드로 강제 |
| 컨트랙트 툴체인 | **Foundry** (forge/anvil) | 빠른 테스트, 포함된 로컬 체인(Anvil) |
| 블록체인(개발) | **Anvil** | 1~3주차 개발용 단일 노드 로컬 체인 |
| 블록체인(시연) | **Hyperledger Besu** (QBFT) | 권한형·즉시 finality. "여러 기관 합의" 시연(후반) |
| 백엔드 | **Java 17+ / Spring Boot** | 작업량 대부분, 주력 강점, 금융권 IT 현실 정합성 |
| 블록체인 연동 | **web3j** | Java에서 트랜잭션·컨트랙트 호출·이벤트 구독 |
| 데이터 | **PostgreSQL** | 권리자명부·잔고·거래내역, 정합성 대사 |
| 인프라 | **Docker / docker-compose** | PostgreSQL·Besu 같은 상태 보유 서비스 띄우기 |

### 3.1 ERC-3643 관련 방침
표준(ERC-3643/T-REX)의 **핵심 개념(온체인 신원 기반 전송 제약)** 을 직접 구현하는 것에서 시작한다. 초반엔 whitelist 기반 단순 모델로 메커니즘을 체득하고, 여유가 되면 표준 인터페이스에 근접시킨다. 표준 라이브러리를 그대로 끌어다 쓰지 않는 이유는 **학습·설명 가능성**이 목적이기 때문이다.

### 3.2 도커 사용 경계
- **도커로:** PostgreSQL(개발 내내), Besu 멀티노드(후반 시연), 최종 통합 데모(`docker compose up`)
- **로컬로:** JDK·Gradle, Foundry(forge/cast), Anvil(개발 체인은 프로세스로 실행), IDE
- 근거: 상태를 가진 서비스는 도커가 유리, 자주 호출하는 개발 CLI는 로컬이 개발 루프상 유리.

### 3.3 Kafka/Redis 제외 근거 (검증됨)
- **Kafka 불필요:** 동기화의 누락 복구는 "특정 블록부터 이벤트 재조회"로 해결된다. **블록체인 자체가 재생 가능한 로그**라 Kafka의 핵심 가치와 중복. fan-out(다중 소비자) 요건이 없으므로 과설계.
- **Redis 불필요:** 단일 백엔드 인스턴스라 분산락 불필요. 멱등성은 `tx_hash` 유니크 제약으로 DB가 처리. 조회 캐시는 본 규모에서 불필요.
- 필요해지는 조건을 문서화해 두되(다중 소비자 → Kafka, 다중 인스턴스/고조회 → Redis), 코어에는 넣지 않는다.

---

## 4. 시스템 구조

```
[투자자/발행인 (mock 클라이언트 / REST 호출)]
        │ REST
        ▼
┌───────────────────────────────────────────────┐
│  계좌관리기관 백엔드 (Spring Boot)                 │
│  ├─ Controller  : 발행/청약/이전/조회 API          │
│  ├─ Service     : 컴플라이언스 사전검증·업무 흐름    │
│  ├─ Chain       : web3j (서명·전송·호출)           │
│  ├─ EventListener: 컨트랙트 이벤트 구독 → DB 반영   │
│  ├─ Reconciler  : 주기 배치, 온체인↔DB 대사        │
│  └─ Repository  : PostgreSQL 접근(JPA)            │
└───────┬───────────────────────────┬─────────────┘
        │ tx / call (web3j)          │ JPA
        ▼                            ▼
┌────────────────────────┐   ┌────────────────────────┐
│ 블록체인 (Anvil/Besu)     │   │ PostgreSQL              │
│ = 전자등록계좌부 (정본)    │   │ investor / security      │
│ ┌────────────────────┐ │   │ holding / transaction    │
│ │ SecurityToken(Sol) │ │   │ sync_checkpoint          │
│ │ - whitelist(KYC)    │ │   └────────────┬────────────┘
│ │ - lockup / maxSupply│ │                │
│ │ - mint / transfer   │ │     Reconciler가 대사·복구
│ │ - emit 이벤트        │ │◀───────────────┘
│ └────────────────────┘ │
└────────────────────────┘
```

### 4.1 온체인/오프체인 책임 분담
| 책임 | 온체인 | 오프체인 |
|---|---|---|
| 권리의 정본(잔고) | ✅ | 미러(조회용) |
| 전송 제약 최종 강제 | ✅ | 사전검증만 |
| 투자자 실명·KYC 데이터 | 식별자만 | ✅ 실데이터 |
| 빠른 조회·집계·명부 | ✕ | ✅ |
| 정합성 대사·복구 | 상태 제공 | ✅ |

### 4.2 핵심 설계 원칙: 컴플라이언스 이중화
백엔드 사전검증(빠른 UX/에러) + 컨트랙트 최종 강제(`require`). 백엔드를 우회해 직접 컨트랙트를 호출해도 온체인에서 막힌다.

---

## 5. 온체인 설계 (SecurityToken 컨트랙트)

### 5.1 상태 변수
- `address agent` — 계좌관리기관(백엔드 서명 계정). 발행/whitelist 권한
- `uint256 totalSupply`, `uint256 maxSupply` — 현재 발행량 / 발행한도
- `mapping(address => uint256) balanceOf` — 보유 잔고
- `mapping(address => bool) whitelisted` — KYC 통과 여부
- `mapping(address => uint64) lockupUntil` — 주소별 락업 해제 시각(unix)

### 5.2 함수 (시그니처 수준)
- `setWhitelist(address investor, bool status)` — onlyAgent
- `setLockup(address investor, uint64 until)` — onlyAgent
- `mint(address to, uint256 amount)` — onlyAgent; require(totalSupply+amount ≤ maxSupply), require(whitelisted[to])
- `transfer(address to, uint256 amount)` — require(whitelisted[msg.sender] && whitelisted[to]); require(block.timestamp ≥ lockupUntil[msg.sender]); require(balanceOf[msg.sender] ≥ amount)
- `agentTransfer(address from, address to, uint256 amount)` — onlyAgent (배정·강제이전 등 계좌관리기관 대행용, 동일 제약 적용)

### 5.3 이벤트
- `event Issued(address indexed to, uint256 amount)`
- `event Transferred(address indexed from, address indexed to, uint256 amount)`
- `event WhitelistUpdated(address indexed investor, bool status)`

> 모든 상태 변경 함수는 대응 이벤트를 emit 한다. 이벤트가 ④ 동기화의 입력이다.

### 5.4 보안 체크리스트
- 접근제어(onlyAgent) 누락 점검
- 체크-이펙트-인터랙션 순서, reentrancy (transfer는 외부호출 없음이라 위험 낮으나 패턴 준수)
- 정수 언더/오버플로우 (0.8+ 기본 보호, 그래도 잔고 검사 명시)

---

## 6. 오프체인 설계 (백엔드)

### 6.1 REST API (초안)
| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/investors` | 투자자 등록 + KYC(mock) → 온체인 whitelist 등록 |
| POST | `/api/securities` | 증권 등록(컨트랙트 주소 연결/배포) |
| POST | `/api/issuance` | 발행(mint) |
| POST | `/api/subscriptions` | 청약 신청 |
| POST | `/api/allocations` | 배정(투자자에게 이전) |
| POST | `/api/transfers` | 권리이전 |
| GET | `/api/securities/{id}/holders` | 권리자명부 조회 |
| GET | `/api/investors/{id}/holdings` | 투자자 잔고 |
| GET | `/api/transactions` | 거래내역 |
| POST | `/api/reconciliation/run` | 수동 대사 트리거 |

### 6.2 데이터 모델 (PostgreSQL)
- `investor(id PK, name, wallet_address UNIQUE, kyc_status, created_at)`
- `security(id PK, name, symbol, contract_address, total_supply, max_supply, created_at)`
- `holding(investor_id FK, security_id FK, balance, PK(investor_id, security_id))`
- `transaction(id PK, security_id FK, tx_hash UNIQUE, block_number, type, from_addr, to_addr, amount, status, created_at)`
- `sync_checkpoint(security_id PK, last_processed_block)`

### 6.3 계층 책임
- **Controller**: 요청 검증, DTO 변환
- **Service**: 업무 흐름 + 컴플라이언스 사전검증(KYC·락업·한도)
- **ChainService(web3j)**: 트랜잭션 서명/전송, 컨트랙트 호출, 영수증 대기
- **EventListener**: 이벤트 구독 → DB 반영
- **Reconciler**: 주기 배치 대사·복구
- **Repository(JPA)**: 영속화

---

## 7. 동기화 & 정합성 설계 (이 프로젝트의 핵심)

### 7.1 실시간 동기화 (④)
- web3j로 `SecurityToken` 이벤트를 `last_processed_block`부터 구독
- 이벤트 수신 시: `holding` upsert, `transaction` insert, `sync_checkpoint` 갱신
- 방향은 항상 **온체인 → DB 단방향**

### 7.2 멱등성
- `transaction.tx_hash`에 UNIQUE 제약 → 동일 이벤트 재수신 시 중복 반영 방지
- 반영 로직은 재실행해도 결과 동일하도록 작성

### 7.3 누락 복구
- 리스너 재기동 시 `sync_checkpoint.last_processed_block`부터 이벤트 재조회
- at-least-once 수신 + 멱등 처리 = 정확히 한 번 반영 효과

### 7.4 정합성 검증 (⑤)
- 주기 배치: 보유자별 온체인 `balanceOf` 조회 → DB `holding`과 대사
- 불일치 시: 로그 기록 후 checkpoint부터 이벤트 재조회로 복구, 복구 후 재대사

### 7.5 finality 방침
- Anvil/Besu(QBFT)는 즉시 finality → 거래 확정 즉시 DB 반영 가능
- (참고) 퍼블릭 체인이라면 N confirmations 대기·reorg 보정이 필요 — 합의 방식에 따라 전략이 달라진다는 점만 문서화. 본 프로젝트엔 해당 없음.

---

## 8. 로컬 실행 구성

### 8.1 필요 도구
- JDK 17+, Gradle(또는 Maven)
- Foundry (forge, cast, anvil)
- Docker / docker-compose
- Git, IDE(IntelliJ)

### 8.2 무엇이 로컬 / 도커
- 로컬 프로세스: Anvil(개발 체인), Spring Boot(개발 중 IDE 실행)
- 도커: PostgreSQL, (후반) Besu 노드들, (최종) 백엔드까지 묶어 통합 데모

### 8.3 docker-compose 구성요소
- `postgres` (개발 내내)
- `besu-node-1..n` (후반 시연, QBFT)
- (최종) `backend` (Spring Boot 이미지)

### 8.4 실행 순서 (개발 단계)
1. `anvil` 실행 (로컬 체인)
2. `forge` 로 컨트랙트 컴파일·테스트·배포 → 컨트랙트 주소 확보
3. `docker compose up postgres`
4. 백엔드 설정에 컨트랙트 주소·RPC URL·DB 접속정보 주입 후 실행
5. REST로 발행→청약→배정→이전 시나리오 호출, DB 동기화·대사 확인

---

## 9. 디렉토리 구조 (제안)

```
sto-prototype/
├─ contracts/                 # Foundry 프로젝트
│  ├─ src/SecurityToken.sol
│  ├─ test/SecurityToken.t.sol
│  └─ script/Deploy.s.sol
├─ backend/                   # Spring Boot
│  ├─ src/main/java/.../controller
│  ├─ src/main/java/.../service
│  ├─ src/main/java/.../chain        # web3j 연동 + EventListener
│  ├─ src/main/java/.../reconcile
│  ├─ src/main/java/.../domain       # 엔티티/리포지토리
│  └─ src/main/resources/application.yml
├─ docker-compose.yml
└─ docs/                      # 본 명세서·아키텍처
```

---

## 10. 구현 순서 (단계별 체크리스트)

### 1주차 — 온체인
- [ ] Foundry 세팅, Anvil 기동
- [ ] SecurityToken 컨트랙트 (whitelist/락업/maxSupply/mint/transfer/agentTransfer)
- [ ] 이벤트 정의·emit
- [ ] forge 테스트 (전송 제약·발행한도·락업 케이스)

### 2주차 — 백엔드 골격
- [ ] Spring Boot + web3j + JPA + PostgreSQL 연결
- [ ] 컨트랙트 래퍼(web3j) 생성, 배포 컨트랙트 연결
- [ ] 투자자 등록(+whitelist), 발행, 청약, 배정, 이전 API
- [ ] 컴플라이언스 사전검증

### 3주차 — 동기화·정합성
- [ ] EventListener: 이벤트 구독 → holding/transaction 반영
- [ ] 멱등성(tx_hash unique)·checkpoint 누락 복구
- [ ] Reconciler 배치: 온체인↔DB 대사·복구
- [ ] 장애 시나리오 테스트(리스너 중단 후 재기동)

### 4주차 — 마감·시연
- [ ] (선택) 수익분배(dividend) 또는 Besu 멀티노드 시연
- [ ] docker-compose 통합, 데모 시나리오 스크립트
- [ ] README·아키텍처 문서, 법제 매핑·트레이드오프 정리

---

## 11. 검토된 설계 결정 (타당성 근거)

| 결정 | 근거 |
|---|---|
| 권한형 BFT(Besu QBFT) 가정 | 즉시 finality → reorg 비쟁점. 정합성 난이도가 "이벤트 전달 신뢰성"으로 집중됨 |
| Kafka 제외 | 블록체인이 이미 재생 가능한 로그. fan-out 요건 없음 → 중복·과설계 |
| Redis 제외 | 단일 인스턴스라 분산락 불필요. 멱등성은 DB 유니크 제약으로 충분 |
| ERC-3643 직접 단순 구현 | 학습·설명 가능성 우선. 표준 라이브러리 통째 도입은 목적과 안 맞음 |
| KYC/실명·메인넷 mock·제외 | 프로토타입 검증 목표(정합성·전송 제약)에 집중하기 위한 경계 설정 |
| 온체인→DB 단방향 동기화 | 정본은 온체인. 양방향은 충돌·복잡도만 키움 |

---

## 부록 A. 용어
- **전자등록계좌부**: 증권 권리관계의 법적 정본 장부. 개정으로 분산원장이 해당 가능.
- **계좌관리기관**: 투자자 계좌 관리 주체(증권사). 본 프로젝트의 시점.
- **전자등록기관**: 전자등록 승인·노드 운영 인가기관(본 프로젝트선 mock).
- **finality**: 확정된 거래가 번복되지 않는 성질.
- **멱등성**: 같은 연산을 여러 번 적용해도 결과가 같음.
- **락업(보호예수)**: 일정 기간 매도/이전을 제한하는 제약.

## 부록 B. 면접 "왜" 포인트
1. 왜 분산원장인가 — 변경불가·공동관리 vs 권한형의 신뢰주체 존재(트레이드오프)
2. 온체인 finality vs DB 일관성 — 합의 방식에 따라 전략이 갈림
3. 이벤트 누락 복구 — checkpoint 재조회 + 멱등
4. 프라이버시 — 온체인 투명성 vs 증권 기밀성, 오프체인 분리로 절충
5. 전송 제약을 왜 컨트랙트에까지 — 백엔드 우회 방지, 코드로 강제되는 신뢰
6. 왜 Kafka/Redis를 안 썼나 — 블록체인이 곧 로그, 단일 인스턴스 → 과설계 회피
