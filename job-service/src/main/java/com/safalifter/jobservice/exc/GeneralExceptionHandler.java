package com.safalifter.jobservice.exc;

import com.safalifter.jobservice.config.trace.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatus status,
                                                                  @NonNull WebRequest request) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors()
                .forEach(x -> errors.put(((FieldError) x).getField(), x.getDefaultMessage()));
        addTraceId(errors);
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(GenericErrorResponse.class)
    public ResponseEntity<?> genericError(GenericErrorResponse exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        String traceId = exception.getTraceId();
        if (traceId == null) {
            traceId = TraceIdUtil.getTraceId();
        }
        if (traceId != null) {
            errors.put("traceId", traceId);
        }
        return new ResponseEntity<>(errors, exception.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public final ResponseEntity<?> handleAllException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", ex.getMessage() != null ? ex.getMessage() : "Internal server error");
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> notFoundException(NotFoundException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> unauthorizedException(UnauthorizedException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> accessDeniedException(AccessDeniedException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(DuplicateFavoriteException.class)
    public ResponseEntity<?> duplicateFavoriteException(DuplicateFavoriteException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<?> illegalStateTransitionException(IllegalStateTransitionException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockingFailureException(ObjectOptimisticLockingFailureException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", "Concurrent update detected. Please refresh and try again.");
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockingFailureException(OptimisticLockingFailureException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", "Concurrent update detected. Please refresh and try again.");
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    private void addTraceId(Map<String, Object> response) {
        String traceId = TraceIdUtil.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            response.put("traceId", traceId);
        }
    }
}
