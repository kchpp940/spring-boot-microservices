package com.safalifter.authservice.orchestration.exception;

import com.safalifter.authservice.exc.GenericErrorResponse;

public class RegistrationBusinessException extends RuntimeException {
    private final GenericErrorResponse businessError;

    public RegistrationBusinessException(GenericErrorResponse businessError) {
        super(businessError.getMessage());
        this.businessError = businessError;
    }

    public GenericErrorResponse getBusinessError() {
        return businessError;
    }
}
