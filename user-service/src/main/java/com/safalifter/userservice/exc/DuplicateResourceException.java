package com.safalifter.userservice.exc;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Builder
@Getter
public class DuplicateResourceException extends RuntimeException {
    private final String field;
    private final String value;
    private final HttpStatus httpStatus;

    public DuplicateResourceException(String field, String value) {
        super(field + " already exists: " + value);
        this.field = field;
        this.value = value;
        this.httpStatus = HttpStatus.CONFLICT;
    }

    public DuplicateResourceException(String field, String value, HttpStatus httpStatus) {
        super(field + " already exists: " + value);
        this.field = field;
        this.value = value;
        this.httpStatus = httpStatus;
    }
}
