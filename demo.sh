#!/usr/bin/env bash
#
# STO 프로토타입 통합 데모.
# 발행 → 청약 → 배정 → 이전 → (자동)동기화 → 정합성 대사를 REST API만으로 시연한다.
#
# 사전조건: Git Bash, Foundry(forge/anvil), Docker Desktop 실행.
# 실행:    ./demo.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
export PATH="$PATH:$HOME/.foundry/bin"

RPC="http://127.0.0.1:8545"
AGENT_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
API="http://localhost:8080"

# Anvil 기본 계정
TREASURY_W="0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" # acct0 = agent = 창고
ALICE_W="0x70997970C51812dc3A010C7d01b50e0d17dc79C8"    # acct1
BOB_W="0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"      # acct2

say() { echo ""; echo "=== $* ==="; }
post() { curl -sf -X POST "$API$1" -H 'Content-Type: application/json' -d "$2"; }
get()  { curl -sf "$API$1"; }
id_of() { grep -oE '"id":[0-9]+' | head -1 | grep -oE '[0-9]+'; }

say "1) Anvil 기동 및 컨트랙트 배포"
if ! cast block-number --rpc-url "$RPC" >/dev/null 2>&1; then
  anvil > /tmp/anvil-demo.log 2>&1 &
  sleep 3
fi
# forge(Windows 바이너리)는 /c/... MSYS 경로를 못 읽으므로 contracts 디렉토리에서 상대경로로 실행
ADDR=$(cd "$ROOT/contracts" && forge create src/SecurityToken.sol:SecurityToken \
  --rpc-url "$RPC" --private-key "$AGENT_KEY" --broadcast \
  --constructor-args "KOFIA Bond" "KBND" 1000000 2>/dev/null \
  | grep -i "Deployed to:" | grep -oE "0x[a-fA-F0-9]{40}")
echo "배포된 컨트랙트: $ADDR"

say "2) 백엔드 jar 빌드"
( cd "$ROOT/backend" && ./gradlew bootJar -q --no-daemon )

say "3) docker compose 기동 (postgres + backend)"
export STO_CHAIN_CONTRACT_ADDRESS="$ADDR"
export STO_CHAIN_RPC_URL="http://host.docker.internal:8545"
export STO_CHAIN_AGENT_PRIVATE_KEY="$AGENT_KEY"
docker compose -f "$ROOT/docker-compose.yml" up -d --build postgres backend

say "4) 백엔드 기동 대기"
for i in $(seq 1 60); do
  if curl -sf "$API/api/transactions" >/dev/null 2>&1; then echo "백엔드 준비됨"; break; fi
  sleep 2
done

say "4.5) DB 초기화(재실행 대비)"
docker exec sto-postgres psql -U sto -d sto -q -c \
  "TRUNCATE subscription, transaction, holding, sync_checkpoint, security, investor RESTART IDENTITY CASCADE;" \
  && echo "초기화 완료"

# 데이터의 이름은 영문 사용(셸/curl UTF-8 인코딩 이슈 회피)
say "5) 투자자/창고 등록"
TRE=$(post /api/investors "{\"name\":\"Treasury\",\"walletAddress\":\"$TREASURY_W\"}" | id_of)
ALICE=$(post /api/investors "{\"name\":\"Alice\",\"walletAddress\":\"$ALICE_W\"}" | id_of)
BOB=$(post /api/investors "{\"name\":\"Bob\",\"walletAddress\":\"$BOB_W\"}" | id_of)
echo "Treasury=$TRE Alice=$ALICE Bob=$BOB"

say "6) 증권 등록 (배포된 컨트랙트 연결)"
SEC=$(post /api/securities "{\"name\":\"KOFIA-Bond\",\"symbol\":\"KBND\",\"maxSupply\":1000000,\"contractAddress\":\"$ADDR\"}" | id_of)
echo "증권 id=$SEC"

say "7) 증권별 whitelist (창고·앨리스·밥)"
post "/api/securities/$SEC/whitelist" "{\"investorId\":$TRE,\"status\":true}" >/dev/null
post "/api/securities/$SEC/whitelist" "{\"investorId\":$ALICE,\"status\":true}" >/dev/null
post "/api/securities/$SEC/whitelist" "{\"investorId\":$BOB,\"status\":true}" >/dev/null
echo "whitelist 완료"

say "8) 발행 ①: 창고에 1000 발행(가용 물량)"
post /api/issuance "{\"securityId\":$SEC,\"investorId\":$TRE,\"amount\":1000}"

say "9) 청약 ②: 앨리스 500 신청"
SUB=$(post /api/subscriptions "{\"securityId\":$SEC,\"investorId\":$ALICE,\"amount\":500}" | id_of)
echo "청약 id=$SUB"

say "10) 배정 ②: 앨리스에게 300 배정(창고→앨리스)"
post /api/allocations "{\"subscriptionId\":$SUB,\"amount\":300}"

say "11) 권리이전 ③: 앨리스 → 밥 100"
post /api/transfers "{\"securityId\":$SEC,\"fromInvestorId\":$ALICE,\"toInvestorId\":$BOB,\"amount\":100}"

say "12) 자동 동기화 대기(스케줄러 2s 주기)"
sleep 5

say "13) 권리자명부 (기대: 창고 700, 앨리스 200, 밥 100)"
get "/api/securities/$SEC/holders"; echo ""

say "14) 앨리스 보유"
get "/api/investors/$ALICE/holdings"; echo ""

say "15) 거래내역"
get "/api/transactions?securityId=$SEC"; echo ""

say "16) 정합성 대사 실행"
post "/api/reconciliation/run?securityId=$SEC" ""; echo ""

say "데모 완료. 정리하려면: docker compose down  (Anvil은 호스트 프로세스)"
