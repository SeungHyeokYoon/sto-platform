package com.sto.platform.error;

/// 자원 충돌(예: 이미 등록된 지갑 주소).
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
