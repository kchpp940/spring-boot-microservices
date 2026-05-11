package com.safalifter.authservice.request;

import com.safalifter.authservice.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {
    private String userId;
    private String offerId;
    private String message;
    private NotificationType notificationType;
    private String traceId;
}
