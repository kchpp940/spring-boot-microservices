package com.safalifter.userservice.dto;

import com.safalifter.userservice.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferencesDto {
    private String userId;
    private Map<NotificationType, Boolean> preferences;
}
