package com.safalifter.authservice.client.adapter;

import com.safalifter.authservice.client.NotificationServiceClient;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.request.SendNotificationRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClientAdapter {

    private final NotificationServiceClient notificationServiceClient;

    public void save(SendNotificationRequest request) {
        try {
            notificationServiceClient.save(request);
        } catch (GenericErrorResponse e) {
            log.warn("Notification-service returned business error: status={}, message={}",
                    e.getHttpStatus(), e.getMessage());
            throw e;
        } catch (FeignException e) {
            log.error("Feign error calling notification-service: status={}, message={}",
                    e.status(), e.getMessage());
            HttpStatus status = HttpStatus.resolve(e.status());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            throw GenericErrorResponse.builder()
                    .httpStatus(status)
                    .message("Service unavailable: notification-service - " + e.getMessage())
                    .build();
        }
    }
}
