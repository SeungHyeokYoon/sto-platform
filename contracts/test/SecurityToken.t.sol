// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {SecurityToken} from "../src/SecurityToken.sol";

contract SecurityTokenTest is Test {
    SecurityToken token;

    address agent = address(this); // 배포자 = agent
    address alice = makeAddr("alice");
    address bob = makeAddr("bob");
    address mallory = makeAddr("mallory"); // 비-whitelist

    uint256 constant MAX = 1_000_000;

    event Issued(address indexed to, uint256 amount);
    event Transferred(address indexed from, address indexed to, uint256 amount);
    event WhitelistUpdated(address indexed investor, bool status);

    function setUp() public {
        token = new SecurityToken("KOFIA Bond", "KBND", MAX);
    }

    // ─── 배포/초기 상태 ─────────────────────────────────────────────
    function test_Constructor_SetsState() public view {
        assertEq(token.agent(), agent);
        assertEq(token.name(), "KOFIA Bond");
        assertEq(token.symbol(), "KBND");
        assertEq(token.maxSupply(), MAX);
        assertEq(token.totalSupply(), 0);
    }

    function test_Constructor_RevertsOnZeroMaxSupply() public {
        vm.expectRevert("ST: maxSupply zero");
        new SecurityToken("X", "X", 0);
    }

    // ─── whitelist ──────────────────────────────────────────────────
    function test_SetWhitelist_OnlyAgent() public {
        vm.prank(mallory);
        vm.expectRevert("ST: not agent");
        token.setWhitelist(alice, true);
    }

    function test_SetWhitelist_EmitsEvent() public {
        vm.expectEmit(true, false, false, true);
        emit WhitelistUpdated(alice, true);
        token.setWhitelist(alice, true);
        assertTrue(token.whitelisted(alice));
    }

    // ─── 발행(mint) ─────────────────────────────────────────────────
    function test_Mint_Success() public {
        token.setWhitelist(alice, true);
        vm.expectEmit(true, false, false, true);
        emit Issued(alice, 100);
        token.mint(alice, 100);
        assertEq(token.balanceOf(alice), 100);
        assertEq(token.totalSupply(), 100);
    }

    function test_Mint_RevertsIfNotWhitelisted() public {
        vm.expectRevert("ST: receiver not whitelisted");
        token.mint(mallory, 100);
    }

    function test_Mint_RevertsIfExceedsMaxSupply() public {
        token.setWhitelist(alice, true);
        vm.expectRevert("ST: exceeds maxSupply");
        token.mint(alice, MAX + 1);
    }

    function test_Mint_AtMaxSupplyBoundary() public {
        token.setWhitelist(alice, true);
        token.mint(alice, MAX); // 경계: 정확히 한도까지 허용
        assertEq(token.totalSupply(), MAX);
        vm.expectRevert("ST: exceeds maxSupply");
        token.mint(alice, 1);
    }

    function test_Mint_OnlyAgent() public {
        token.setWhitelist(alice, true);
        vm.prank(mallory);
        vm.expectRevert("ST: not agent");
        token.mint(alice, 100);
    }

    // ─── 권리이전(transfer) ─────────────────────────────────────────
    function _seed(address to, uint256 amount) internal {
        token.setWhitelist(to, true);
        token.mint(to, amount);
    }

    function test_Transfer_Success() public {
        _seed(alice, 500);
        token.setWhitelist(bob, true);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit Transferred(alice, bob, 200);
        token.transfer(bob, 200);

        assertEq(token.balanceOf(alice), 300);
        assertEq(token.balanceOf(bob), 200);
    }

    function test_Transfer_RevertsIfSenderNotWhitelisted() public {
        // bob whitelist, alice는 잔고만 있고 whitelist 해제 상태 가정
        _seed(alice, 500);
        token.setWhitelist(bob, true);
        token.setWhitelist(alice, false);

        vm.prank(alice);
        vm.expectRevert("ST: sender not whitelisted");
        token.transfer(bob, 100);
    }

    function test_Transfer_RevertsIfReceiverNotWhitelisted() public {
        _seed(alice, 500);
        vm.prank(alice);
        vm.expectRevert("ST: receiver not whitelisted");
        token.transfer(mallory, 100);
    }

    function test_Transfer_RevertsIfLockedUp() public {
        _seed(alice, 500);
        token.setWhitelist(bob, true);
        token.setLockup(alice, uint64(block.timestamp + 1 days));

        vm.prank(alice);
        vm.expectRevert("ST: sender locked up");
        token.transfer(bob, 100);
    }

    function test_Transfer_SucceedsAfterLockupExpires() public {
        _seed(alice, 500);
        token.setWhitelist(bob, true);
        uint64 until = uint64(block.timestamp + 1 days);
        token.setLockup(alice, until);

        vm.warp(until); // 락업 해제 시각 도달(경계)
        vm.prank(alice);
        token.transfer(bob, 100);
        assertEq(token.balanceOf(bob), 100);
    }

    function test_Transfer_RevertsIfInsufficientBalance() public {
        _seed(alice, 50);
        token.setWhitelist(bob, true);
        vm.prank(alice);
        vm.expectRevert("ST: insufficient balance");
        token.transfer(bob, 100);
    }

    // ─── 대행 이전(agentTransfer) ───────────────────────────────────
    function test_AgentTransfer_Success() public {
        _seed(alice, 500);
        token.setWhitelist(bob, true);
        token.agentTransfer(alice, bob, 300);
        assertEq(token.balanceOf(alice), 200);
        assertEq(token.balanceOf(bob), 300);
    }

    function test_AgentTransfer_OnlyAgent() public {
        _seed(alice, 500);
        token.setWhitelist(bob, true);
        vm.prank(mallory);
        vm.expectRevert("ST: not agent");
        token.agentTransfer(alice, bob, 100);
    }

    function test_AgentTransfer_AppliesSameConstraints() public {
        _seed(alice, 500);
        // bob 미-whitelist → 대행이어도 제약 동일 적용
        vm.expectRevert("ST: receiver not whitelisted");
        token.agentTransfer(alice, bob, 100);
    }

    // ─── 락업 설정 권한 ─────────────────────────────────────────────
    function test_SetLockup_OnlyAgent() public {
        vm.prank(mallory);
        vm.expectRevert("ST: not agent");
        token.setLockup(alice, uint64(block.timestamp + 1 days));
    }

    // ─── agent 이전 ─────────────────────────────────────────────────
    function test_TransferAgent_Works() public {
        token.transferAgent(bob);
        assertEq(token.agent(), bob);
        // 이전 agent는 권한 상실
        vm.expectRevert("ST: not agent");
        token.setWhitelist(alice, true);
    }
}
