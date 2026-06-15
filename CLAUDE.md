# 작업 지침 (Claude Code)

이 저장소는 토큰증권(STO) 발행·유통 프로토타입이다. 전체 명세는 [STO_claude.md](STO_claude.md)가 단일 기준 문서(source of truth)다. 구현 전 항상 이 명세를 따른다.

## 핵심 규칙

### 1. 임의 판단 금지
- **명세에 없거나 모호한 결정은 절대 임의로 판단하지 않는다.**
- 선택지가 생기면(라이브러리 버전, 디렉토리명, 빌드 도구, 설계 변경 등) 진행을 멈추고 **반드시 사용자에게 물어본다.**
- 명세와 다르게 구현해야 할 이유가 생기면 먼저 사용자에게 보고하고 확인을 받는다.
- "아마 이게 맞겠지"로 진행하지 않는다. 불확실하면 질문한다.

### 2. 커밋 규칙
- 작업을 **중간중간 의미 단위로 커밋**한다.
- 커밋 메시지는 **짧고 간단하게** 쓴다.
- **Conventional Commits 기본 규칙**을 지킨다: `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:` 등 타입 접두어 사용.
- `by claudecode`, `Co-Authored-By: Claude` 같은 **생성 도구 표기를 절대 넣지 않는다.**
- 예시: `feat: SecurityToken 컨트랙트 추가`, `test: 락업 전송 제약 테스트`, `docs: 작업 지침 추가`

## 기술 스택 (명세 기준)
- 스마트컨트랙트: Solidity ^0.8.x + Foundry (forge/anvil)
- 백엔드: Java 17+ / Spring Boot + web3j + JPA
- 데이터: PostgreSQL
- 인프라: Docker / docker-compose
- 블록체인: Anvil(개발) → Hyperledger Besu QBFT(후반 시연)
