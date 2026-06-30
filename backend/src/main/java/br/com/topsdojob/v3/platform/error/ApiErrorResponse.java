package br.com.topsdojob.v3.platform.error;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ApiErrorCode code,
        String message,
        String path,
        String requestId) {
}
