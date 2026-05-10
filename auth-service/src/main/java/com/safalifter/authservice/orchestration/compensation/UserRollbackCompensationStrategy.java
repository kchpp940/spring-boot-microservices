package com.safalifter.authservice.orchestration.compensation;

import com.safalifter.authservice.client.adapter.UserServiceClientAdapter;
import com.safalifter.authservice.orchestration.RegistrationContext;
import com.safalifter.authservice.orchestration.exception.RegistrationCompensationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRollbackCompensationStrategy implements CompensationStrategy {

    private final UserServiceClientAdapter userServiceClientAdapter;

    @Override
    public String getName() {
        return "USER_ROLLBACK_COMPENSATION_STRATEGY";
    }

    @Override
    public boolean shouldApply(RegistrationContext context) {
        return context.hasUserBeenCreated() && !"USER_CREATION".equals(context.getFailedStep());
    }

    @Override
    public void compensate(RegistrationContext context) {
        String userId = context.getUserId();
        log.warn("Executing user rollback compensation for userId={}", userId);

        try {
            userServiceClientAdapter.internalHardDeleteUserById(userId);
            log.info("User rollback completed successfully for userId={}", userId);
        } catch (Exception e) {
            log.error("Unexpected error during user rollback for userId={}", userId, e);
            throw new RegistrationCompensationException(
                    "Unexpected error during user rollback: " + e.getMessage(),
                    e,
                    context.getFailedStep(),
                    "USER_ROLLBACK"
            );
        }
    }
}
