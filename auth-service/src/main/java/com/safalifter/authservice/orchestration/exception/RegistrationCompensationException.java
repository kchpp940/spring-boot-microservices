package com.safalifter.authservice.orchestration.exception;

public class RegistrationCompensationException extends RuntimeException {
    private final String failedStep;
    private final String compensationStep;

    public RegistrationCompensationException(String message, String failedStep, String compensationStep) {
        super(message);
        this.failedStep = failedStep;
        this.compensationStep = compensationStep;
    }

    public RegistrationCompensationException(String message, Throwable cause, String failedStep, String compensationStep) {
        super(message, cause);
        this.failedStep = failedStep;
        this.compensationStep = compensationStep;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public String getCompensationStep() {
        return compensationStep;
    }
}
