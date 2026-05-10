package com.safalifter.authservice.service;

import com.safalifter.authservice.client.NotificationServiceClient;
import com.safalifter.authservice.client.UserServiceClient;
import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.enums.NotificationType;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.request.RegisterRequest;
import com.safalifter.authservice.request.SendNotificationRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationOrchestrationService {

    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    public RegisterDto orchestrateRegistration(RegisterRequest request) {
        String userId = null;
        RegisterDto registeredUser = null;

        try {
            registeredUser = callUserServiceToCreateUser(request);
            userId = registeredUser.getId();

            callNotificationServiceToSendWelcomeNotification(registeredUser);

            return registeredUser;

        } catch (Exception e) {
            log.error("Registration orchestration failed at userId={}, error={}", userId, e.getMessage());

            if (userId != null) {
                try {
                    compensateUserServiceToRollback(userId);
                } catch (Exception rollbackEx) {
                    log.error("Rollback failed for userId={}, error={}", userId, rollbackEx.getMessage());
                }
            }

            if (e instanceof GenericErrorResponse) {
                throw e;
            }
            if (e instanceof FeignException) {
                throw GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Registration failed: " + e.getMessage())
                        .build();
            }
            throw GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Registration failed")
                    .build();
        }
    }

    private RegisterDto callUserServiceToCreateUser(RegisterRequest request) {
        log.info("Calling user-service to create user: {}", request.getUsername());
        ResponseEntity<RegisterDto> response = userServiceClient.save(request);
        if (response == null || response.getBody() == null) {
            throw GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Failed to create user: empty response from user-service")
                    .build();
        }
        log.info("User created successfully: userId={}", response.getBody().getId());
        return response.getBody();
    }

    private void callNotificationServiceToSendWelcomeNotification(RegisterDto user) {
        log.info("Calling notification-service to send welcome notification for userId={}", user.getId());
        SendNotificationRequest welcomeRequest = SendNotificationRequest.builder()
                .userId(user.getId())
                .message("Welcome to our platform, " + user.getUsername() + "!")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .build();
        ResponseEntity<Void> response = notificationServiceClient.save(welcomeRequest);
        if (response == null) {
            throw GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Failed to send welcome notification: empty response from notification-service")
                    .build();
        }
        log.info("Welcome notification sent successfully for userId={}", user.getId());
    }

    private void compensateUserServiceToRollback(String userId) {
        log.warn("Compensating: hard deleting user for rollback, userId={}", userId);
        try {
            userServiceClient.internalHardDeleteUserById(userId);
            log.info("User rollback (hard delete) completed for userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to rollback (hard delete) user creation for userId={}, error={}", userId, e.getMessage());
            throw GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Rollback failed for user " + userId)
                    .build();
        }
    }
}
