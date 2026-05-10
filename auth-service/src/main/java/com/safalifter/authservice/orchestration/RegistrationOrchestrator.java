package com.safalifter.authservice.orchestration;

import com.safalifter.authservice.client.NotificationServiceClient;
import com.safalifter.authservice.client.UserServiceClient;
import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.enums.NotificationType;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.orchestration.compensation.CompensationStrategy;
import com.safalifter.authservice.orchestration.exception.RegistrationBusinessException;
import com.safalifter.authservice.orchestration.exception.RegistrationCompensationException;
import com.safalifter.authservice.orchestration.exception.RegistrationFeignException;
import com.safalifter.authservice.request.RegisterRequest;
import com.safalifter.authservice.request.SendNotificationRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationOrchestrator {

    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final List<CompensationStrategy> compensationStrategies;

    public RegisterDto orchestrate(RegisterRequest request) {
        RegistrationContext context = RegistrationContext.builder()
                .request(request)
                .build();

        try {
            return executeRegistrationSteps(context);
        } catch (RegistrationBusinessException e) {
            log.info("Registration failed due to business validation: {}", e.getMessage());
            if (context.hasUserBeenCreated()) {
                log.warn("User was created before business exception, triggering rollback");
                handleFailure(context, e);
            }
            throw e;
        } catch (RegistrationFeignException e) {
            log.error("Registration failed due to Feign error. Service={}, Operation={}, Status={}",
                    e.getServiceName(), e.getOperation(), e.getStatus());
            handleFailure(context, e);
            throw wrapFeignException(e);
        } catch (RegistrationCompensationException e) {
            log.error("Registration and compensation both failed. FailedStep={}, CompensationStep={}",
                    e.getFailedStep(), e.getCompensationStep());
            throw wrapCompensationException(e);
        } catch (Exception e) {
            log.error("Unexpected registration error", e);
            context.setFailureCause(e);
            handleFailure(context, e);
            throw GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Registration failed: " + e.getMessage())
                    .build();
        }
    }

    private RegisterDto executeRegistrationSteps(RegistrationContext context) {
        log.info("Starting registration orchestration for user: {}", context.getRequest().getUsername());

        RegisterDto user = createUserInUserService(context);
        context.setRegisteredUser(user);
        log.info("Step 1/2 completed: USER_CREATION, userId={}", user.getId());

        sendWelcomeNotification(context);
        context.setNotificationSent(true);
        log.info("Step 2/2 completed: NOTIFICATION_SEND");

        log.info("Registration orchestration completed successfully for user: {}", user.getUsername());
        return user;
    }

    private RegisterDto createUserInUserService(RegistrationContext context) {
        log.info("Calling user-service to create user: {}", context.getRequest().getUsername());
        
        try {
            ResponseEntity<RegisterDto> response = userServiceClient.save(context.getRequest());
            
            if (response == null || response.getBody() == null) {
                throw GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Failed to create user: empty response from user-service")
                        .build();
            }
            
            log.info("User created successfully: userId={}", response.getBody().getId());
            return response.getBody();
            
        } catch (GenericErrorResponse e) {
            log.info("User-service returned business error: status={}, message={}",
                    e.getHttpStatus(), e.getMessage());
            context.setFailedStep("USER_CREATION");
            context.setFailureCause(e);
            throw new RegistrationBusinessException(e);
        } catch (FeignException e) {
            log.error("Feign error calling user-service: status={}, message={}",
                    e.status(), e.getMessage());
            context.setFailedStep("USER_CREATION");
            context.setFailureCause(e);
            throw new RegistrationFeignException("user-service", "USER_CREATION", e);
        }
    }

    private void sendWelcomeNotification(RegistrationContext context) {
        RegisterDto user = context.getRegisteredUser();
        log.info("Calling notification-service to send welcome notification for userId={}", user.getId());

        SendNotificationRequest welcomeRequest = SendNotificationRequest.builder()
                .userId(user.getId())
                .message("Welcome to our platform, " + user.getUsername() + "!")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .build();

        try {
            ResponseEntity<Void> response = notificationServiceClient.save(welcomeRequest);
            
            if (response == null) {
                throw GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Failed to send welcome notification: empty response from notification-service")
                        .build();
            }
            
            log.info("Welcome notification sent successfully for userId={}", user.getId());
            
        } catch (GenericErrorResponse e) {
            log.warn("Notification-service returned business error: status={}, message={}",
                    e.getHttpStatus(), e.getMessage());
            throw new RegistrationBusinessException(e);
        } catch (FeignException e) {
            log.error("Feign error calling notification-service: status={}, message={}",
                    e.status(), e.getMessage());
            throw new RegistrationFeignException("notification-service", "NOTIFICATION_SEND", e);
        }
    }

    private void handleFailure(RegistrationContext context, Exception cause) {
        log.warn("Handling registration failure. FailedStep={}, userId={}",
                context.getFailedStep(), context.getUserId());

        CompensationStrategy strategy = findCompensationStrategy(context);
        
        if (strategy == null) {
            log.info("No compensation strategy applicable for this failure");
            return;
        }

        log.info("Applying compensation strategy: {}", strategy.getName());
        
        try {
            strategy.compensate(context);
            log.info("Compensation strategy {} completed successfully", strategy.getName());
        } catch (RegistrationCompensationException e) {
            context.setCompensationError(e.getMessage());
            throw e;
        } catch (Exception e) {
            context.setCompensationError(e.getMessage());
            throw new RegistrationCompensationException(
                    "Compensation failed: " + e.getMessage(),
                    e,
                    context.getFailedStep(),
                    strategy.getName()
            );
        }
    }

    private CompensationStrategy findCompensationStrategy(RegistrationContext context) {
        return compensationStrategies.stream()
                .filter(strategy -> strategy.shouldApply(context))
                .findFirst()
                .orElse(null);
    }

    private GenericErrorResponse wrapFeignException(RegistrationFeignException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus());
        if (status == null || !status.is5xxServerError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return GenericErrorResponse.builder()
                .httpStatus(status)
                .message("Service unavailable: " + e.getServiceName() + " - " + e.getOperation())
                .build();
    }

    private GenericErrorResponse wrapCompensationException(RegistrationCompensationException e) {
        return GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Critical failure: registration and rollback both failed. Failed at: " +
                        e.getFailedStep() + ", rollback failed at: " + e.getCompensationStep())
                .build();
    }
}
