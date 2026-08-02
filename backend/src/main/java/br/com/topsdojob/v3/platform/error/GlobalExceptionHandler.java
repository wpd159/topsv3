package br.com.topsdojob.v3.platform.error;

import java.time.Instant;

import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.application.publico.anunciante.StoryJaAtivoException;
import br.com.topsdojob.v3.application.publico.premium.PremiumOfertaAtualizadaException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return build(ApiErrorCode.BAD_REQUEST, request);
    }

    @ExceptionHandler({
            BindException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception, HttpServletRequest request) {
        return build(ApiErrorCode.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        return build(fromStatus(exception.getStatusCode().value()), request);
    }

    @ExceptionHandler(StoryJaAtivoException.class)
    public ResponseEntity<ApiErrorResponse> handleStoryJaAtivo(
            StoryJaAtivoException exception,
            HttpServletRequest request) {
        return build(ApiErrorCode.STORY_JA_ATIVO, request);
    }

    @ExceptionHandler(PremiumOfertaAtualizadaException.class)
    public ResponseEntity<ApiErrorResponse> handlePremiumOfertaAtualizada(
            PremiumOfertaAtualizadaException exception,
            HttpServletRequest request) {
        return build(ApiErrorCode.PREMIUM_OFERTA_ATUALIZADA, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            RuntimeException exception,
            HttpServletRequest request) {
        return build(ApiErrorCode.FORBIDDEN, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return build(ApiErrorCode.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return build(ApiErrorCode.NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIdContext.current(request);
        LOGGER.error(
                "unexpected_error requestId={} exception={}",
                requestId,
                exception.getClass().getName());
        return build(ApiErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiErrorResponse> build(ApiErrorCode code, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                code.status().value(),
                code.status().getReasonPhrase(),
                code,
                code.defaultMessage(),
                request.getRequestURI(),
                RequestIdContext.current(request));
        return ResponseEntity.status(code.status()).body(response);
    }

    private ApiErrorCode fromStatus(int statusCode) {
        for (ApiErrorCode code : ApiErrorCode.values()) {
            if (code.status().value() == statusCode) {
                return code;
            }
        }
        return ApiErrorCode.INTERNAL_ERROR;
    }
}
