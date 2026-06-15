package com.sto.platform.error;

/// 자원을 찾지 못함.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
