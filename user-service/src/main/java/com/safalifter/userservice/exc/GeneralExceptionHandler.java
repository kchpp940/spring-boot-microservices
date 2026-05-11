package com.safalifter.userservice.exc;

import com.safalifter.userservice.config.trace.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });
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

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<?> duplicateResourceException(DuplicateResourceException exception) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        errors.put("field", exception.getField());
        errors.put("value", exception.getValue());
        addTraceId(errors);
        return new ResponseEntity<>(errors, exception.getHttpStatus());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> dataIntegrityViolationException(DataIntegrityViolationException exception) {
        Map<String, Object> errors = new HashMap<>();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";

        if (containsAny(message, "username", "uk_username", "idx_username")) {
            errors.put("error", "username already exists");
            errors.put("field", "username");
        } else if (containsAny(message, "email", "uk_email", "idx_email")) {
            errors.put("error", "email already exists");
            errors.put("field", "email");
        } else {
            errors.put("error", "Data integrity violation: duplicate resource");
        }
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.CONFLICT);
    }

    private boolean containsAny(String source, String... targets) {
        if (source == null) return false;
        for (String target : targets) {
            if (source.contains(target.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void addTraceId(Map<String, Object> response) {
        String traceId = TraceIdUtil.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            response.put("traceId", traceId);
        }
    }
}
