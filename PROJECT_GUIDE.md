# STO 프로토타입 — 완전 분석 가이드

> 이 문서 하나로 프로젝트 전체를 이해할 수 있도록 만든 **해체분석 가이드**다.
> "어디서부터, 무엇을, 어떤 순서로 보고, 무엇이 중요한가"를 따라가며 읽으면 된다.
> 더 짧은 진입점은 [README.md](README.md), 설계 근거는 [docs/architecture.md](docs/architecture.md),
> 원본 명세(단일 기준)는 [STO_claude.md](STO_claude.md).

---

## 0. 이 문서를 읽는 법

이 프로젝트는 **"블록체인이라는 정본 장부 + DB라는 사본 장부, 둘을 어긋나지 않게 유지한다"**가 전부다.
그래서 분석도 그 한 문장을 중심으로 본다.

읽는 순서(권장):
1. **개요**로 "무엇을/왜"를 잡는다 (§1)
2. **큰 그림** 5분 — 두 개의 장부와 데이터 흐름 (§2)
3. **권장 코드 투어** — 정본(컨트랙트) → 다리(web3j) → 사본(DB) → 업무 API → ⭐동기화 → ⭐대사 (§3)
4. **세부 심층 분석** — 각 부분의 내부 메커니즘과 "왜" (§4)
5. **눈여겨볼 핵심 포인트**와 면접 Q&A (§5, §9)
6. **파일 지도**로 전체 조망 (§6)

⭐ 표시(동기화·대사)가 이 프로젝트의 진짜 핵심이다. 시간이 없으면 그 두 개만 봐도 된다.

---

## 1. 개요 — 무엇을, 왜

### 1.1 한 줄 정의
증권사(계좌관리기관) 자리에서 토큰증권을 **발행·청약·배정·권리이전**하고, 그 결과를
**온체인 정본과 오프체인 권리자명부 DB 사이에 정합성 있게 유지**하는 백엔드 + 스마트컨트랙트.

### 1.2 배경 (왜 지금)
2026년 1월 전자증권법·자본시장법 개정으로 **분산원장이 법적 전자등록계좌부로 인정**되고
(시행 예정 2027년 1월), 증권사가 계좌관리기관/유통 인프라를 직접 운영하게 된다.
이 프로젝트는 그 구조를 로컬 PC 한 대로 축소 재현한 것이다.

### 1.3 관점과 목적
- **관점**: 증권사(계좌관리기관) 시점. 발행인 요청·전자등록기관 승인은 mock.
- **목적**: 금융권/IT 포트폴리오. 평가 포인트는 화려함이 아니라
  **① 온/오프체인 정합성, ② 전송 제약의 코드 강제, ③ 장애 상황 처리**.

### 1.4 등장인물 (도메인 → 구현)
| 주체 | 현실 | 이 프로젝트 |
|---|---|---|
| 발행인 | 증권을 찍는 회사 | mock(REST 요청 시뮬레이션) |
| 전자등록기관 | 한국예탁결제원(KSD) 등 | mock(자동 승인) |
| **계좌관리기관(증권사)** | 투자자 계좌·장부 운영 | **← 이 백엔드 (서명 계정 = agent)** |
| 투자자 | 증권을 사는 사람 | REST 클라이언트 |

---

## 2. 큰 그림 5분 — 두 개의 장부

이 프로젝트에서 "장부(권리자명부)"는 **두 벌** 존재한다. 이게 핵심이다.

| | 어디에 | 역할 | 비유 |
|---|---|---|---|
| **온체인 장부** | 블록체인 컨트랙트 `balanceOf` | **정본(법적 진짜)** | 법적으로 인정되는 원장 |
| **오프체인 장부** | PostgreSQL `holding` | **미러(사본)** | 빠른 조회용 복사본 |

왜 두 벌인가? 블록체인은 위변조 불가·법적 정본이지만 "이름으로 검색", "종목별 보유자 목록"
같은 조회가 느리고 불편하다. 그래서 **정본은 온체인, 조회는 DB 사본**으로 나누고,
**둘을 일치시키는 것**이 이 프로젝트의 본질이다.

### 데이터 흐름 (발행 한 건이 흐르는 길)
```
[REST] 발행 요청
   │
   ▼ (백엔드 사전검증: KYC·whitelist·한도)
ChainService.mint()  ──tx 서명·전송──►  블록체인: SecurityToken.mint()
                                            │  - require로 최종 강제
                                            │  - balanceOf 변경 (정본)
                                            └─ emit Issued(to, amount)  ← 이벤트
                                                       │
   DB는 아직 안 바뀜  ◄── (단방향 원칙) ───────────────┘
                                                       │
SyncService (주기 폴링)  ──ethGetLogs──►  이벤트 조회
   │  holding += amount, transaction insert, checkpoint 전진
   ▼
DB 사본이 정본과 일치  ──►  GET /holders 에서 보임
```

**핵심 원칙: 쓰기는 온체인에만. DB는 이벤트를 통해 온체인→DB 단방향으로 따라온다.**

---

## 3. 권장 코드 투어 (이 순서로 읽어라)

### Step 1 ─ 정본 장부: 컨트랙트 `SecurityToken.sol`
📂 [contracts/src/SecurityToken.sol](contracts/src/SecurityToken.sol)

여기가 **권리의 정본**이자 **전송 제약이 코드로 강제되는 곳**. 먼저 상태 변수를 본다:
- `balanceOf` (보유 잔고 = 장부), `whitelisted` (KYC 통과), `lockupUntil` (락업), `maxSupply` (발행한도), `agent` (계좌관리기관).

**가장 중요한 두 함수**, 발행과 이전의 제약:
```solidity
function mint(address to, uint256 amount) external onlyAgent {       // L68
    require(amount > 0, "ST: amount zero");
    require(whitelisted[to], "ST: receiver not whitelisted");        // KYC 필수
    require(totalSupply + amount <= maxSupply, "ST: exceeds maxSupply"); // 한도
    ...
    emit Issued(to, amount);
}

function _enforceTransfer(address from, address to, uint256 amount) internal { // L99
    require(whitelisted[from], "ST: sender not whitelisted");        // 양측 KYC
    require(whitelisted[to],   "ST: receiver not whitelisted");
    require(block.timestamp >= lockupUntil[from], "ST: sender locked up"); // 락업
    require(balanceOf[from] >= amount, "ST: insufficient balance");
    ...
    emit Transferred(from, to, amount);
}
```

**눈여겨볼 점**
- **`require`가 최종 방어선**이다. 백엔드를 우회해 컨트랙트를 직접 호출해도 여기서 막힌다.
- 모든 상태변경 함수는 **대응 이벤트를 emit**한다 (`Issued`/`Transferred`). 이 이벤트가 §Step6 동기화의 입력이다.
- `transfer`(본인 서명)와 `agentTransfer`(계좌관리기관 대행, `onlyAgent`)가 분리돼 있다. 백엔드는 agent로 서명하므로 **대행 함수**를 쓴다 (이유는 §4.4).

### Step 2 ─ 규칙을 테스트로 이해
📂 [contracts/test/SecurityToken.t.sol](contracts/test/SecurityToken.t.sol) (20개 테스트)

컨트랙트의 "되는 것/안 되는 것"이 테스트에 다 적혀 있다. 특히 락업 경계, 발행한도 경계,
whitelist 미통과 거부, 대행 이전도 동일 제약 적용 등을 확인하라. `forge test`로 즉시 실행된다.

### Step 3 ─ 백엔드 ↔ 체인 다리: `ChainService`
📂 [backend/.../chain/ChainService.java](backend/src/main/java/com/sto/platform/chain/ChainService.java)

자바에서 컨트랙트를 부르는 통로. 증권마다 컨트랙트 주소가 다르므로 **주소를 받아 래퍼를 동적 load**한다.
- 쓰기(서명 필요): `mint`, `agentTransfer`, `setWhitelist`, `setLockup`
- 읽기: `balanceOf`, `totalSupply`, `maxSupply`, `isWhitelisted`, `lockupUntil`
- `agentAddress()` = 계좌관리기관 계정 = **창고(treasury)** (§4.3)

**눈여겨볼 점**: 서명 키는 시작 시점이 아니라 **실제 서명할 때 지연 로딩**한다(`credentials()`).
키가 없어도 앱 구동·읽기는 가능. 키는 환경변수로 주입(`application.yml`의 `${STO_CHAIN_AGENT_PRIVATE_KEY:}`).

> 컨트랙트 래퍼(`SecurityToken.java`)는 web3j-gradle-plugin이 `contracts/src`를 solc 0.8.28로
> 컴파일해 **빌드 시 자동 생성**한다(`build/`, 커밋 안 함). 소스는 `.sol` 한 곳만 관리.

### Step 4 ─ 사본 장부: 데이터 모델
📂 [backend/.../domain/](backend/src/main/java/com/sto/platform/domain/)

DB 미러의 구조 (명세 6.2):
- `Investor` (지갑주소 UNIQUE, KYC 상태)
- `Security` (컨트랙트 주소, totalSupply/maxSupply)
- `Holding` (투자자×증권 복합키, 잔고) ← **온체인 balanceOf의 미러**
- `TransactionRecord` (`tx_hash` **UNIQUE** ← 멱등성의 열쇠)
- `SyncCheckpoint` (증권별 마지막 처리 블록 ← 누락 복구의 열쇠)
- `Subscription` (청약, 명세 데이터모델 확장)

**눈여겨볼 점**: 온체인 잔고는 `uint256`이므로 DB는 `BigInteger`(NUMERIC)로 매핑해 오버플로우를 막았다.

### Step 5 ─ 업무 API 흐름: Service + Controller
📂 [service/](backend/src/main/java/com/sto/platform/service/) · [controller/](backend/src/main/java/com/sto/platform/controller/)

각 업무는 **사전검증 → 온체인 호출** 패턴이 동일하다. 발행을 예로:
```java
// IssuanceService.issue() — 컴플라이언스 사전검증(빠른 실패), 최종강제는 컨트랙트가 담당
if (investor.getKycStatus() != KycStatus.APPROVED) throw ...("수령자 KYC ...");   // L50
if (!chainService.isWhitelisted(address, wallet)) throw ...("whitelist ...");      // L53
if (total.add(amount) > maxSupply) throw ...("발행한도 초과");                      // L57
TransactionReceipt receipt = chainService.mint(address, wallet, amount);           // L61
// ← DB는 여기서 안 건드린다. 명부는 Step6 동기화로 채워진다.
```

업무별 포인트:
- **발행**(IssuanceService): mint. 수령자 KYC·whitelist·한도 검증.
- **청약**(SubscriptionService): 신청을 PENDING으로 DB 저장.
- **배정**(AllocationService): **창고→투자자** `agentTransfer`. 배정량 ≤ 신청량 && ≤ 가용물량(창고 잔고).
- **이전**(TransferService): 투자자→투자자 `agentTransfer`. 양측 whitelist·락업·잔고 검증.

**눈여겨볼 점 — 컴플라이언스 이중화**: 백엔드 검증은 **UX·빠른 에러용**이고,
진짜 강제는 컨트랙트 `require`다. 둘 다 있어야 "우회 불가 + 좋은 에러"가 동시에 성립.

### Step 6 ─ ⭐ 핵심: 동기화 `SyncService`
📂 [backend/.../sync/SyncService.java](backend/src/main/java/com/sto/platform/sync/SyncService.java)

온체인 이벤트를 DB 미러로 가져오는 곳. **폴링 방식**: checkpoint 이후 블록의 로그를 조회 → 반영 → checkpoint 전진.
```java
private boolean applyLog(Security security, Log evlog) {              // L98
    String txHash = evlog.getTransactionHash();
    if (transactionRepository.existsByTxHash(txHash)) return false;   // L101 ★멱등성★
    if (ISSUED_TOPIC.equals(topic0)) { ... applyIssue(...); }         // L106 이벤트 디코딩
    else if (TRANSFERRED_TOPIC.equals(topic0)) { ... applyTransfer(...); }
}
```
`applyIssue`/`applyTransfer`가 `holding` upsert + `transaction` insert + `security.totalSupply` 갱신.
범위 처리 후 `saveCheckpoint(securityId, latest)`로 진척 저장.

**여기서 가장 중요한 3가지**
1. **방향은 온체인→DB 단방향.** DB에서 체인으로 거꾸로 쓰지 않는다.
2. **멱등성** = `existsByTxHash` 가드 + `tx_hash` UNIQUE. 같은 이벤트 두 번 받아도 한 번만 반영.
3. **누락 복구** = `sync_checkpoint`. 리스너가 죽었다 살아나도 마지막 블록부터 재조회해 따라잡는다.
   → at-least-once 수신 + 멱등 처리 = **정확히 한 번 반영** 효과.

📂 [SyncScheduler.java](backend/src/main/java/com/sto/platform/sync/SyncScheduler.java): `@Scheduled`로 주기 폴링(기본 2초).
테스트에선 `sto.sync.scheduling-enabled=false`로 끈다.

### Step 7 ─ ⭐ 핵심: 정합성 대사 `ReconcileService`
📂 [backend/.../sync/ReconcileService.java](backend/src/main/java/com/sto/platform/sync/ReconcileService.java)

동기화가 완벽해도, 만일을 대비해 **온체인 잔고 ↔ DB 잔고를 대사**하고 어긋나면 정본으로 복구한다(명세 7.4):
1. 먼저 `syncSecurity()`로 누락 이벤트 재조회(1차 복구)
2. `security.totalSupply`를 온체인 값으로 보정
3. 투자자별 `balanceOf`(온체인) vs `holding`(DB) 비교 → 불일치 시 **온체인(정본) 값으로 DB 보정** + 로그
4. 결과 리포트(검사 수/불일치/보정) 반환

트리거: `POST /api/reconciliation/run` (수동) 또는 배치.

**눈여겨볼 점**: 정본은 항상 온체인. 충돌 시 DB를 체인에 맞춘다(반대 아님). 단방향 원칙의 연장.

### Step 8 ─ 전체를 한 번에: 데모와 통합 테스트
- 📂 [demo.sh](demo.sh): Anvil 기동→배포→`docker compose up`→발행·청약·배정·이전→자동 동기화→대사를 REST로 시연.
- 📂 [SyncFlowIT.java](backend/src/test/java/com/sto/platform/SyncFlowIT.java): **이 프로젝트를 한 테스트로 증명** —
  온체인 작업 → DB 비어있음 확인 → 동기화로 명부 채워짐 → 멱등성(재동기화 0) → DB 오염 → 대사로 정본 복구.

> **시간이 없다면 [SyncFlowIT.java](backend/src/test/java/com/sto/platform/SyncFlowIT.java) 하나만 읽어도
> 이 프로젝트가 무엇을 하는지 다 보인다.**

---

## 4. 세부 심층 분석

### 4.1 컴플라이언스 이중화 — 왜 두 군데서 막나
- **백엔드 사전검증**(Service): 빠른 실패·친절한 에러(HTTP 422 등). UX용.
- **컨트랙트 `require`**: 최종 강제. 백엔드를 우회해도 못 뚫음. 신뢰의 근거.
- 둘 중 하나만으론 부족: 백엔드만 있으면 우회 가능, 컨트랙트만 있으면 에러 UX가 나쁨.
- 전역 예외 처리([error/GlobalExceptionHandler.java](backend/src/main/java/com/sto/platform/error/GlobalExceptionHandler.java))가
  검증 실패(400)·업무규칙 위반(422)·충돌(409)·체인오류(502)로 매핑.

### 4.2 단방향 동기화의 정확한 메커니즘
- **왜 폴링(웹소켓 아님)?** 누락 복구 모델이 "checkpoint부터 **재조회**"인데, 이게 곧 폴링이다.
  웹소켓 푸시도 복구 땐 어차피 재조회가 필요하므로, 폴링이 멱등·복구·테스트에 더 단순·견고. 짧은 주기로 사실상 실시간.
- **이벤트 디코딩**: 생성된 래퍼의 `ISSUED_EVENT`/`TRANSFERRED_EVENT` 시그니처로 토픽 매칭 후
  `Contract.staticExtractEventParameters`로 파라미터 추출.
- **창고/agent 주소는 투자자가 아니면** holding을 만들지 않는다(명부는 등록 투자자 대상). totalSupply는 발행량 전체 반영.

### 4.3 창고(treasury) 모델 — 배정의 "가용 물량"
- 명세 "배정량 ≤ **가용 물량**"의 가용 물량 = 미배정 보유분 = **창고 잔고**.
- 창고 = **계좌관리기관(agent) 계정 자체**(`chainService.agentAddress()`). 별도 필드 없이 자연스러움.
- 흐름: 발행은 창고로 mint(가용물량 적재) → 배정은 창고→투자자 `agentTransfer`.
- 데모에선 창고를 투자자로 등록해 REST만으로 funding한다.

### 4.4 `agentTransfer` vs `transfer` — 왜 대행을 쓰나
- 컨트랙트 `transfer`는 `msg.sender`(본인)가 보내는 모델. 그러나 **백엔드는 투자자 개인키가 없다.**
- 백엔드는 **agent 키로 서명**하므로, 계좌관리기관 대행 함수 `agentTransfer(from, to, amount)`(`onlyAgent`)를 쓴다.
- 대행이어도 **전송 제약은 동일**하게 `require`로 강제된다(테스트 `test_AgentTransfer_AppliesSameConstraints`).

### 4.5 서명 키·환경변수
- 평문 키를 설정 파일에 두지 않는다. `application.yml`은 `${STO_CHAIN_AGENT_PRIVATE_KEY:}`로 env 주입.
- 로컬 개발용 Anvil 0번 키는 [.env.example](.env.example)에만(개발 전용 명시).

### 4.6 finality
- Anvil/Besu(QBFT)는 **즉시 finality** → reorg 비쟁점. 그래서 정합성 난이도가 "이벤트 전달 신뢰성"으로 집중된다.
- 퍼블릭 체인이라면 N confirmations·reorg 보정이 필요(본 프로젝트엔 해당 없음, 문서로만 언급).

### 4.7 멀티노드 합의 (Besu QBFT) — 구현됨
- 개발/테스트는 Anvil(단일 노드), "여러 기관 합의" 시연은 **Besu QBFT 검증자 4개**([docker-compose.besu.yml](docker-compose.besu.yml), [besu/README.md](besu/README.md)).
- 검증자들이 **라운드로빈으로 블록을 제안·합의**(2초 주기). 제안자가 4개 노드를 번갈아 도는 것으로 "공동 운영"을 확인할 수 있다.
- **전환은 RPC 주소만**: `STO_CHAIN_RPC_URL`을 Anvil→Besu로. 백엔드 코드·컨트랙트 불변(web3j가 Besu에서 mint/transfer 처리 검증 완료).
- 포크는 Berlin이라 **legacy 가스 거래**를 쓴다(foundry `evm_version=berlin`, forge 배포 시 `--legacy`). 그래서 PUSH0(Shanghai) 미사용.
- "블록 공동 생성"과 "발행 권한 공유"는 별개: 발행은 여전히 단일 `agent`(`onlyAgent`).

---

## 5. 눈여겨볼 핵심 포인트 (평가용)

| 포인트 | 어디서 보이나 |
|---|---|
| 온/오프체인 정합성(단방향·멱등·대사복구) | [SyncService](backend/src/main/java/com/sto/platform/sync/SyncService.java), [ReconcileService](backend/src/main/java/com/sto/platform/sync/ReconcileService.java), [SyncFlowIT](backend/src/test/java/com/sto/platform/SyncFlowIT.java) |
| 전송 제약의 코드 강제 | [SecurityToken.sol](contracts/src/SecurityToken.sol) `require` + [contract test](contracts/test/SecurityToken.t.sol) |
| 컴플라이언스 이중화 | 각 Service 사전검증 + 컨트랙트 require |
| 장애 처리(누락복구) | `sync_checkpoint` + `existsByTxHash` 멱등 |
| 과설계 회피(Kafka/Redis 제외) | [docs/architecture.md](docs/architecture.md) §4 |

---

## 6. 파일 지도 (전체 조망)

```
contracts/
  src/SecurityToken.sol        ★ 정본 장부 + 전송제약(require) + 이벤트
  test/SecurityToken.t.sol     컨트랙트 규칙 테스트 20개
  script/Deploy.s.sol          배포 스크립트

backend/src/main/java/com/sto/platform/
  chain/
    ChainService.java          ★ web3j 다리: 서명·전송·조회, 창고 주소
    Web3jConfig.java           Web3j/가스 빈
    ChainProperties.java       sto.chain.* 설정(env 주입)
    contracts/SecurityToken.java  (빌드 자동생성 래퍼, 미커밋)
  controller/                  REST 엔드포인트 (명세 6.1)
  service/
    IssuanceService            발행(사전검증 → mint)
    SubscriptionService        청약 저장
    AllocationService          배정(창고 → 투자자)
    TransferService            권리이전
    SecurityService            증권 등록 + 증권별 whitelist
    InvestorService            투자자 등록(+KYC mock)
    QueryService               명부·잔고·거래내역 조회
  sync/
    SyncService.java           ★ 이벤트 폴링 → DB 반영(멱등·checkpoint)
    SyncScheduler.java         주기 실행(@Scheduled)
    ReconcileService.java      ★ 정합성 대사·복구
  domain/                      JPA 엔티티/리포지토리(사본 장부 구조)
  dto/                         요청/응답
  error/                       전역 예외 → HTTP 매핑

besu/                          ★ Besu QBFT 멀티노드: genesis·검증자 키 + README(시연 절차)
docker-compose.yml             postgres + (통합) backend
docker-compose.besu.yml        Besu QBFT 검증자 4개(멀티노드 합의 시연)
demo.sh                        통합 데모(전 과정 REST 시연, Anvil 기반)
README.md / docs/architecture.md / STO_claude.md(명세) / CLAUDE.md(작업지침)
```

---

## 7. 데이터/이벤트 흐름 — 배정 한 건 따라가기

```
POST /api/allocations {subscriptionId, amount}
  │
  ▼ AllocationService.allocate()
  ├─ 청약 PENDING 확인, amount ≤ 신청량
  ├─ 투자자 whitelist 확인(온체인)
  ├─ amount ≤ 창고 잔고(가용물량) 확인(온체인 balanceOf)
  ├─ ChainService.agentTransfer(창고 → 투자자)  ──► 블록체인
  │                                                  └─ emit Transferred(창고, 투자자, amount)
  └─ subscription.status = ALLOCATED (DB)
                                                       │
  (2초 후) SyncScheduler → SyncService.syncSecurity()  ◄┘ 이벤트 폴링
      holding[투자자] += amount, transaction insert, checkpoint 전진
                                                       │
GET /api/securities/{id}/holders  ◄────────────────────┘ 명부에 반영되어 보임
```

---

## 8. 한계·트레이드오프·확장 지점

- **범위 밖(의도적)**: 실 KYC/AML 연동, 메인넷 배포, HA/다중 인스턴스, 실 거래소 연동.
- **Kafka/Redis 제외**: 블록체인이 곧 재생 가능한 로그, 단일 인스턴스 → 과설계 회피.
  필요 조건: 다중 소비자→Kafka, 다중 인스턴스/고조회→Redis.
- **미구현(명세 선택 항목)**: 수익분배(dividend), Besu QBFT 멀티노드("여러 기관 합의") 시연.
- **확장**: ERC-3643 표준 인터페이스 근접, 퍼블릭 체인 시 reorg/confirmations 전략.

---

## 9. 면접 "왜" Q&A

1. **왜 분산원장인가?** 변경불가·공동관리 vs 권한형의 신뢰주체 존재(트레이드오프).
2. **온체인 finality vs DB 일관성?** 합의 방식(권한형 BFT/퍼블릭)에 따라 전략이 갈림. 본 프로젝트는 즉시 finality라 reorg 비쟁점.
3. **이벤트 누락은 어떻게 복구?** `sync_checkpoint`부터 재조회 + `tx_hash` 멱등 = exactly-once 효과.
4. **프라이버시?** 온체인엔 주소(식별자)만, 실명·KYC 실데이터는 오프체인 분리.
5. **전송 제약을 왜 컨트랙트에까지?** 백엔드 우회 방지. 코드로 강제되는 신뢰(이중화).
6. **왜 Kafka/Redis를 안 썼나?** 블록체인이 곧 로그, 단일 인스턴스 → 과설계 회피.
7. **백엔드가 왜 transfer가 아니라 agentTransfer?** 투자자 키가 없고 agent로 서명하므로 계좌관리기관 대행. 제약은 동일.

---

## 10. 빠른 실행 메모

```bash
# 통합 데모 한 방
./demo.sh

# 테스트
cd contracts && forge test          # 컨트랙트 20개
cd backend  && ./gradlew test       # 백엔드(실체인 IT는 env 없으면 skip)
```
실체인 통합 테스트는 `STO_CHAIN_CONTRACT_ADDRESS` 환경변수가 있을 때만 실행된다.
```
