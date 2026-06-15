// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {SecurityToken} from "../src/SecurityToken.sol";

/// @notice 로컬 체인(Anvil) 배포 스크립트.
///         실행 예:
///         forge script script/Deploy.s.sol \
///           --rpc-url http://127.0.0.1:8545 \
///           --private-key <AGENT_PRIVATE_KEY> --broadcast
contract Deploy is Script {
    function run() external returns (SecurityToken token) {
        // 환경변수로 파라미터 주입(없으면 기본값).
        string memory name = vm.envOr("TOKEN_NAME", string("KOFIA Bond"));
        string memory symbol = vm.envOr("TOKEN_SYMBOL", string("KBND"));
        uint256 maxSupply = vm.envOr("TOKEN_MAX_SUPPLY", uint256(1_000_000));

        vm.startBroadcast();
        token = new SecurityToken(name, symbol, maxSupply);
        vm.stopBroadcast();

        console.log("SecurityToken deployed at:", address(token));
        console.log("  agent     :", token.agent());
        console.log("  maxSupply :", token.maxSupply());
    }
}
