package com.safalifter.authservice.orchestration.exception;

import feign.FeignException;

public class RegistrationFeignException extends RuntimeException {
    private final String serviceName;
    private final String operation;
    private final FeignException feignException;

    public RegistrationFeignException(String serviceName, String operation, FeignException feignException) {
        super("Feign call to " + serviceName + " failed during " + operation + ": " + feignException.getMessage(), feignException);
        this.serviceName = serviceName;
        this.operation = operation;
        this.feignException = feignException;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOperation() {
        return operation;
    }

    public FeignException getFeignException() {
        return feignException;
    }

    public int getStatus() {
        return feignException != null ? feignException.status() : 500;
    }
}
