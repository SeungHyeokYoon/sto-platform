// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title SecurityToken
/// @notice 토큰증권(STO) 프로토타입용 권한형 증권 토큰.
///         계좌관리기관(agent)이 발행/whitelist/락업을 관리하고,
///         전송 제약(KYC·락업·발행한도)을 컨트랙트에서 최종 강제한다.
/// @dev    명세 5장 기준. 표준 라이브러리 미사용(학습·설명 가능성 목적).
contract SecurityToken {
    // ─── 상태 변수 (명세 5.1) ───────────────────────────────────────
    address public agent;            // 계좌관리기관(백엔드 서명 계정)
    string public name;
    string public symbol;
    uint256 public totalSupply;      // 현재 발행량
    uint256 public maxSupply;        // 발행한도

    mapping(address => uint256) public balanceOf;   // 보유 잔고
    mapping(address => bool) public whitelisted;     // KYC 통과 여부
    mapping(address => uint64) public lockupUntil;   // 락업 해제 시각(unix)

    // ─── 이벤트 (명세 5.3) ──────────────────────────────────────────
    event Issued(address indexed to, uint256 amount);
    event Transferred(address indexed from, address indexed to, uint256 amount);
    event WhitelistUpdated(address indexed investor, bool status);
    event LockupUpdated(address indexed investor, uint64 until);
    event AgentTransferred(address indexed newAgent);

    // ─── 접근 제어 ──────────────────────────────────────────────────
    modifier onlyAgent() {
        require(msg.sender == agent, "ST: not agent");
        _;
    }

    constructor(string memory _name, string memory _symbol, uint256 _maxSupply) {
        require(_maxSupply > 0, "ST: maxSupply zero");
        agent = msg.sender;
        name = _name;
        symbol = _symbol;
        maxSupply = _maxSupply;
    }

    // ─── 관리 함수 (명세 5.2) ───────────────────────────────────────

    /// @notice agent 권한 이전(운영용).
    function transferAgent(address newAgent) external onlyAgent {
        require(newAgent != address(0), "ST: zero agent");
        agent = newAgent;
        emit AgentTransferred(newAgent);
    }

    /// @notice 투자자 whitelist(KYC 통과) 설정.
    function setWhitelist(address investor, bool status) external onlyAgent {
        require(investor != address(0), "ST: zero address");
        whitelisted[investor] = status;
        emit WhitelistUpdated(investor, status);
    }

    /// @notice 투자자 락업 해제 시각 설정.
    function setLockup(address investor, uint64 until) external onlyAgent {
        require(investor != address(0), "ST: zero address");
        lockupUntil[investor] = until;
        emit LockupUpdated(investor, until);
    }

    // ─── 발행 (명세 ①) ──────────────────────────────────────────────

    /// @notice 토큰 발행. 발행한도 초과 불가, 수령자 KYC 필수.
    function mint(address to, uint256 amount) external onlyAgent {
        require(amount > 0, "ST: amount zero");
        require(whitelisted[to], "ST: receiver not whitelisted");
        require(totalSupply + amount <= maxSupply, "ST: exceeds maxSupply");

        // effects
        totalSupply += amount;
        balanceOf[to] += amount;

        emit Issued(to, amount);
    }

    // ─── 권리이전 (명세 ③) ──────────────────────────────────────────

    /// @notice 투자자 간 이전. whitelist 양측 통과 + 락업 미경과 거부.
    function transfer(address to, uint256 amount) external returns (bool) {
        _enforceTransfer(msg.sender, to, amount);
        return true;
    }

    /// @notice 계좌관리기관 대행 이전(배정·강제이전 등). 동일 제약 적용.
    function agentTransfer(address from, address to, uint256 amount)
        external
        onlyAgent
        returns (bool)
    {
        _enforceTransfer(from, to, amount);
        return true;
    }

    /// @dev 전송 제약 공통 강제 로직 (체크 → 이펙트 순서).
    function _enforceTransfer(address from, address to, uint256 amount) internal {
        require(amount > 0, "ST: amount zero");
        require(whitelisted[from], "ST: sender not whitelisted");
        require(whitelisted[to], "ST: receiver not whitelisted");
        require(block.timestamp >= lockupUntil[from], "ST: sender locked up");
        require(balanceOf[from] >= amount, "ST: insufficient balance");

        // effects
        balanceOf[from] -= amount;
        balanceOf[to] += amount;

        emit Transferred(from, to, amount);
    }
}
