package com.safalifter.authservice.orchestration.compensation;

import com.safalifter.authservice.client.UserServiceClient;
import com.safalifter.authservice.orchestration.RegistrationContext;
import com.safalifter.authservice.orchestration.exception.RegistrationCompensationException;
import com.safalifter.authservice.orchestration.exception.RegistrationFeignException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRollbackCompensationStrategy implements CompensationStrategy {

    private final UserServiceClient userServiceClient;

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
            userServiceClient.internalHardDeleteUserById(userId);
            log.info("User rollback completed successfully for userId={}", userId);
        } catch (FeignException feignEx) {
            log.error("Feign exception during user rollback for userId={}, status={}, message={}", 
                    userId, feignEx.status(), feignEx.getMessage());
            throw new RegistrationCompensationException(
                    "Feign error during user rollback: " + feignEx.getMessage(),
                    feignEx,
                    context.getFailedStep(),
                    "USER_ROLLBACK"
            );
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
