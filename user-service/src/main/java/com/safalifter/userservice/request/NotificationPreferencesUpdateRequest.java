package com.safalifter.userservice.request;

import com.safalifter.userservice.enums.NotificationType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class NotificationPreferencesUpdateRequest {
    @NotBlank(message = "UserId is required")
    private String userId;

    private Map<NotificationType, Boolean> preferences;
}
