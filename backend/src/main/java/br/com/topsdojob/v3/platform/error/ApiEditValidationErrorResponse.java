package br.com.topsdojob.v3.platform.error;

import java.time.Instant;

/** Existing API error fields plus safe edit-validation details. */
public record ApiEditValidationErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ApiErrorCode code,
        String message,
        String path,
        String requestId,
        String field,
        String ruleCode) {
}
