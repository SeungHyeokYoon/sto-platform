package com.sto.platform.error;

import java.time.Instant;

/// REST 오류 응답 형식.
public record ApiError(Instant timestamp, int status, String error, String message) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
