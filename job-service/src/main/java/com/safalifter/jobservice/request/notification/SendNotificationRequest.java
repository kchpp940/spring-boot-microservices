package com.safalifter.jobservice.request.notification;

import com.safalifter.jobservice.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {
    private String userId;
    private String offerId;
    private String message;
    private NotificationType notificationType;
}
