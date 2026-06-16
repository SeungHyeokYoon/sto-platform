# 아키텍처 · 법제 매핑 · 트레이드오프

## 1. 시스템 구조

```
[투자자/발행인 (REST 클라이언트)]
        │ REST
        ▼
┌───────────────────────────────────────────────┐
│  계좌관리기관 백엔드 (Spring Boot)                 │
│  ├─ Controller  : 발행/청약/배정/이전/조회 API      │
│  ├─ Service     : 컴플라이언스 사전검증·업무 흐름    │
│  ├─ ChainService: web3j (서명·전송·호출)          │
│  ├─ SyncService : 이벤트 폴링 → DB 반영           │
│  ├─ ReconcileService : 주기/수동 대사·복구         │
│  └─ Repository  : PostgreSQL(JPA)               │
└───────┬───────────────────────────┬─────────────┘
        │ tx / call (web3j)          │ JPA
        ▼                            ▼
┌────────────────────────┐   ┌────────────────────────┐
│ 블록체인 (Anvil/Besu)     │   │ PostgreSQL              │
│ = 전자등록계좌부 (정본)    │   │ investor / security      │
│  SecurityToken.sol       │   │ holding / transaction    │
│  - whitelist / lockup    │   │ subscription             │
│  - maxSupply / mint      │   │ sync_checkpoint          │
│  - transfer / agentXfer  │◀──│   ReconcileService 대사   │
│  - emit 이벤트            │   └────────────┬────────────┘
└──────────┬─────────────┘                │
           └── 이벤트 ──► SyncService ──────┘ (온체인→DB 단방향)
```

## 2. 온체인/오프체인 책임 분담

| 책임 | 온체인 | 오프체인(DB) |
|---|---|---|
| 권리의 정본(잔고) | ✅ 정본 | 미러(조회용) |
| 전송 제약 최종 강제 | ✅ `require` | 사전검증만 |
| 투자자 실명·KYC 데이터 | 식별자(주소)만 | ✅ 실데이터 |
| 빠른 조회·집계·명부 | ✕ | ✅ |
| 정합성 대사·복구 | 상태 제공 | ✅ 수행 |

### 컴플라이언스 이중화
모든 쓰기 API는 **백엔드 사전검증(KYC·whitelist·락업·한도·잔고)** 으로 빠르게 실패시키고,
**컨트랙트 `require`** 로 최종 강제한다. 백엔드를 우회해 컨트랙트를 직접 호출해도 온체인에서 막힌다.

## 3. 동기화 & 정합성 (이 프로젝트의 핵심)

### 단방향 동기화 (온체인 → DB)
- 쓰기 API는 **온체인만 변경**한다(mint/agentTransfer). DB 명부는 직접 쓰지 않는다.
- `SyncService`가 `sync_checkpoint.last_processed_block` 이후 블록의 이벤트 로그를 **폴링**(web3j `ethGetLogs`)해
  `holding`/`transaction`을 반영하고 checkpoint를 전진시킨다. `SyncScheduler`가 주기 실행.
- 방향은 항상 온체인→DB. 양방향은 충돌·복잡도만 키우므로 배제.

### 멱등성
- `transaction.tx_hash` UNIQUE + 반영 전 존재 확인 → 동일 이벤트 재수신 시 중복 반영 방지.
- at-least-once 수신 + 멱등 처리 = 정확히 한 번 반영 효과.

### 누락 복구 & 정합성 대사
- 리스너가 멈췄다 재기동해도 checkpoint부터 재조회하므로 누락 이벤트를 따라잡는다.
- `ReconcileService`: 보유자별 온체인 `balanceOf` ↔ DB `holding` 대사 → 불일치 시 **온체인(정본) 값으로 DB 보정**.
- 절차(명세 7.4): 이벤트 재조회로 1차 복구 → 재대사 → 남은 불일치는 정본 기준 보정.

### finality
Anvil/Besu(QBFT)는 즉시 finality → reorg 비쟁점. 정합성 난이도가 "이벤트 전달 신뢰성"으로 집중된다.
(퍼블릭 체인이라면 N confirmations·reorg 보정이 필요하나 본 프로젝트엔 해당 없음.)

### 멀티노드 합의 (Besu QBFT) — 구현됨
- 개발/테스트는 Anvil(단일 노드), "여러 기관 합의" 시연은 **Besu QBFT 검증자 4개**([docker-compose.besu.yml](../docker-compose.besu.yml)).
- QBFT는 검증자들이 **라운드로빈으로 블록을 제안·합의**(블록 주기 2초, 즉시 finality). n≥3f+1 → 1개 장애까지 견딤.
- **백엔드 코드·컨트랙트는 동일**. 전환은 `STO_CHAIN_RPC_URL`(RPC 주소)만 Anvil→Besu로 바꾸면 된다.
- 포크는 Berlin(EIP-1559 없음) → web3j/forge 모두 **legacy 가스 거래**로 호환(검증 완료: web3j가 Besu에서 mint/transfer 처리).
- 주의: "여러 노드가 블록 공동 생성" ≠ "여러 기관이 발행 권한 공유". 발행 권한은 여전히 단일 `agent`(`onlyAgent`).
  권한까지 분산하려면 컨트랙트 권한 모델(멀티 agent/멀티시그)을 별도로 바꿔야 한다. 자세히는 [besu/README.md](../besu/README.md).

## 4. 검토된 설계 결정 (트레이드오프)

| 결정 | 근거 |
|---|---|
| 온체인→DB 단방향 동기화 | 정본은 온체인. 양방향은 충돌·복잡도만 키움. |
| 이벤트 **폴링**(웹소켓 아님) | 복구 모델(checkpoint 재조회)과 동일 메커니즘 → 멱등·복구·테스트에 유리. 짧은 주기로 사실상 실시간. |
| Kafka 제외 | 블록체인 자체가 재생 가능한 로그. fan-out 요건 없음 → 중복·과설계. |
| Redis 제외 | 단일 인스턴스라 분산락 불필요. 멱등성은 DB UNIQUE 제약으로 충분. |
| ERC-3643 직접 단순 구현 | 학습·설명 가능성 우선. 표준 라이브러리 통째 도입은 목적과 안 맞음. |
| whitelist를 투자자 등록과 분리, **증권별**로 부여 | whitelist는 컨트랙트(증권)마다 다름. 등록=KYC, 거래 허가=증권별로 모델이 정확. |
| 배정 토큰을 **창고(treasury=계좌관리기관 계정)** 에서 이전 | "배정량 ≤ 가용 물량"의 가용 물량 = 창고 잔고. 발행은 창고로, 배정은 창고→투자자 agentTransfer. |
| 대행 이전(`agentTransfer`) 사용 | 백엔드는 agent로 서명하므로 투자자 본인 서명형 `transfer` 대신 계좌관리기관 대행 이전을 사용. |
| 서명 키 환경변수 주입 | 평문 키를 설정파일에 두지 않음. 로컬 개발 키는 `.env.example`에만. |

필요해지는 조건(문서화): 다중 소비자 → Kafka, 다중 인스턴스/고조회 → Redis. 코어에는 넣지 않는다.

## 5. 법제 매핑

| 도메인 개념 | 본 프로토타입 구현 |
|---|---|
| 전자등록계좌부(법적 정본 장부) | 블록체인 위 `SecurityToken` 컨트랙트의 `balanceOf` |
| 계좌관리기관(증권사) | 본 백엔드(서명 계정 = agent) |
| 전자등록기관(예: KSD) | mock(자동 승인) |
| 발행인 | mock(발행 요청을 REST로 시뮬레이션) |
| 전자등록(발행) | `mint` + `Issued` 이벤트 → DB 명부 반영 |
| 권리이전 | `agentTransfer` + `Transferred` 이벤트 |
| 보호예수(락업) | `lockupUntil` 매핑 + 전송 시 `require` |

> 배경: 2026년 1월 전자증권법·자본시장법 개정으로 분산원장이 법적 전자등록계좌부로 인정되고
> (시행 예정 2027년 1월), 증권사가 계좌관리기관/유통 인프라를 직접 운영하게 된다.
> 본 프로젝트는 그 구조를 로컬에서 축소 재현한다.

## 6. 면접 "왜" 포인트

1. **왜 분산원장인가** — 변경불가·공동관리 vs 권한형의 신뢰주체 존재(트레이드오프).
2. **온체인 finality vs DB 일관성** — 합의 방식(권한형 BFT vs 퍼블릭)에 따라 전략이 갈림.
3. **이벤트 누락 복구** — checkpoint 재조회 + 멱등(`tx_hash`) = exactly-once 효과.
4. **프라이버시** — 온체인 투명성 vs 증권 기밀성. 온체인엔 주소만, 실데이터는 오프체인 분리로 절충.
5. **전송 제약을 왜 컨트랙트에까지** — 백엔드 우회 방지, 코드로 강제되는 신뢰(컴플라이언스 이중화).
6. **왜 Kafka/Redis를 안 썼나** — 블록체인이 곧 로그, 단일 인스턴스 → 과설계 회피.
