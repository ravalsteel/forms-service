package com.ravalgroups.forms.shared.web;

import com.ravalgroups.forms.shared.api.ApiError;
import com.ravalgroups.forms.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final ApiError INTERNAL = new ApiError("INTERNAL_ERROR", "An unexpected error occurred");

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> optimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "FORM_VERSION_CONCURRENT_MODIFICATION",
                        "Draft form version was modified by another request; reload and retry"));
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> domain(DomainException exception) {
        HttpStatus status = mapDomainStatus(exception.code());
        return ResponseEntity.status(status).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "Validation failed" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError("MALFORMED_REQUEST", "Request body could not be read"));
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    ResponseEntity<ApiError> unauthenticated(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHENTICATED", "Authentication required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> dataAccess(DataAccessException exception) {
        log.error("Database error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(INTERNAL);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(INTERNAL);
    }

    private static HttpStatus mapDomainStatus(String code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (code) {
            case "NOT_FOUND",
                    "FORM_NOT_FOUND",
                    "FORM_VERSION_NOT_FOUND",
                    "FORM_RUN_NOT_FOUND",
                    "RESPONSE_NOT_FOUND",
                    "TEMPLATE_NOT_FOUND",
                    "EXPORT_NOT_FOUND",
                    "INVITATION_NOT_FOUND",
                    "ROLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN",
                    "CROSS_COMPANY_ACCESS_DENIED",
                    "ANONYMOUS_RESPONSE_ACCESS_DENIED",
                    "COMPANY_CONTEXT_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "CONFLICT",
                    "CONCURRENT_MODIFICATION",
                    "FORM_VERSION_CONCURRENT_MODIFICATION",
                    "FORM_VERSION_MISMATCH",
                    "FORM_VERSION_NOT_EDITABLE",
                    "FORM_RUN_NOT_OPEN",
                    "RESPONSE_ALREADY_SUBMITTED",
                    "IDEMPOTENCY_KEY_REUSED",
                    "DUPLICATE",
                    "BOOTSTRAP_CLOSED",
                    "LAST_ADMIN",
                    "IN_USE" -> HttpStatus.CONFLICT;
            case "BUSINESS_RULE",
                    "INVALID_STATE",
                    "FORM_VERSION_NOT_PUBLISHED",
                    "INVALID_FORM_DEFINITION",
                    "INVALID_ANSWER",
                    "INVALID_QUESTION_REFERENCE",
                    "EXPORT_NOT_READY" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "UNAUTHORIZED", "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "INVALID_PAGE", "INVALID_SIZE", "INVALID_SORT", "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
