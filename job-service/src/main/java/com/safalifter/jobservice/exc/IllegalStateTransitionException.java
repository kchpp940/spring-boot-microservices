package com.safalifter.jobservice.exc;

import org.springframework.http.HttpStatus;

public class IllegalStateTransitionException extends GenericErrorResponse {

    public IllegalStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
