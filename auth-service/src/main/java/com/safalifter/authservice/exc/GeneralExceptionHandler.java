package com.safalifter.authservice.exc;

import com.safalifter.authservice.orchestration.exception.RegistrationBusinessException;
import lombok.extern.slf4j.Slf4j;
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
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(RegistrationBusinessException.class)
    public ResponseEntity<?> handleRegistrationBusinessException(RegistrationBusinessException exception) {
        log.info("Registration business exception: {}", exception.getBusinessError().getMessage());
        GenericErrorResponse businessError = exception.getBusinessError();
        Map<String, String> errors = new HashMap<>();
        errors.put("error", businessError.getMessage());
        return new ResponseEntity<>(errors, businessError.getHttpStatus());
    }

    @ExceptionHandler(GenericErrorResponse.class)
    public ResponseEntity<?> genericError(GenericErrorResponse exception) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        return new ResponseEntity<>(errors, exception.getHttpStatus());
    }

    @ExceptionHandler(WrongCredentialsException.class)
    public ResponseEntity<?> usernameOrPasswordInvalidException(WrongCredentialsException exception) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<?> validationException(ValidationException exception) {
        return ResponseEntity.badRequest().body(exception.getValidationErrors());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> accessDeniedException(AccessDeniedException exception) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", exception.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public final ResponseEntity<?> handleAllException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage() != null ? ex.getMessage() : "Internal server error");
        return new ResponseEntity<>(errors, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
