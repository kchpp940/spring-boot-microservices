package com.safalifter.filestorage.exc;

import com.safalifter.filestorage.config.trace.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public final ResponseEntity<?> handleAllException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", ex.getMessage() != null ? ex.getMessage() : "Internal server error");
        addTraceId(errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.warn("File upload size exceeded", exc);
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", "File too large!");
        addTraceId(errors);
        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                .body(errors);
    }

    private void addTraceId(Map<String, Object> response) {
        String traceId = TraceIdUtil.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            response.put("traceId", traceId);
        }
    }
}
