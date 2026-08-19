package com.devpilot.common.exception;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.api.Result;
import com.devpilot.runtime.lifecycle.IllegalLifecycleTransitionException;
import com.devpilot.runtime.session.SessionStreamNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts backend exceptions to stable, non-sensitive HTTP responses. */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles explicit business failures.
     *
     * @param exception business failure
     * @return client-safe response
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.errorCode().status())
                .body(Result.failure(exception.errorCode(), exception.getMessage()));
    }

    /**
     * Handles a runtime transition the lifecycle state machine does not allow, for example starting
     * a second turn while one is running.
     *
     * @param exception rejected transition
     * @return client-safe conflict response
     */
    @ExceptionHandler(IllegalLifecycleTransitionException.class)
    public ResponseEntity<Result<Void>> handleIllegalLifecycleTransition(
            IllegalLifecycleTransitionException exception) {
        return ResponseEntity.status(ErrorCode.LIFECYCLE_CONFLICT.status())
                .body(Result.failure(ErrorCode.LIFECYCLE_CONFLICT, exception.getMessage()));
    }

    /**
     * Handles a request for a session that has no event stream.
     *
     * @param exception missing session
     * @return client-safe not-found response
     */
    @ExceptionHandler(SessionStreamNotFoundException.class)
    public ResponseEntity<Result<Void>> handleSessionStreamNotFound(SessionStreamNotFoundException exception) {
        return ResponseEntity.status(ErrorCode.SESSION_NOT_FOUND.status())
                .body(Result.failure(ErrorCode.SESSION_NOT_FOUND, exception.getMessage()));
    }

    /**
     * Handles request body validation failures.
     *
     * @param exception validation failure
     * @return client-safe response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = fieldError == null
                ? ErrorCode.INVALID_ARGUMENT.defaultMessage()
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(Result.failure(ErrorCode.INVALID_ARGUMENT, message));
    }

    /**
     * Handles method parameter validation failures.
     *
     * @param exception validation failure
     * @return client-safe response
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(Result.failure(ErrorCode.INVALID_ARGUMENT, exception.getMessage()));
    }

    /**
     * Handles unexpected failures without exposing their stack traces.
     *
     * @param exception unexpected failure
     * @return generic server error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled backend exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }
}

