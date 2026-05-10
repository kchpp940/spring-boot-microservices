package com.safalifter.authservice.orchestration.compensation;

import com.safalifter.authservice.orchestration.RegistrationContext;

public interface CompensationStrategy {
    String getName();

    boolean shouldApply(RegistrationContext context);

    void compensate(RegistrationContext context);
}
