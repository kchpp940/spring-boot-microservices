package com.safalifter.authservice.orchestration;

import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.request.RegisterRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RegistrationContext {
    private RegisterRequest request;
    private RegisterDto registeredUser;
    private boolean notificationSent;
    private String failedStep;
    private Exception failureCause;
    private String compensationError;

    public String getUserId() {
        return registeredUser != null ? registeredUser.getId() : null;
    }

    public boolean hasUserBeenCreated() {
        return registeredUser != null && registeredUser.getId() != null;
    }
}
