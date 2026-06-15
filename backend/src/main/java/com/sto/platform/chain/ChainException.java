package com.sto.platform.chain;

/// 체인 연동 중 발생한 오류(연결·트랜잭션·조회 실패 등).
public class ChainException extends RuntimeException {

    public ChainException(String message) {
        super(message);
    }

    public ChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
