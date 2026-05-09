package com.safalifter.jobservice.dto;

import com.safalifter.jobservice.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.EnumMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferencesDto {
    private String userId;
    private Map<NotificationType, Boolean> preferences;

    public static NotificationPreferencesDto createDefault(String userId) {
        Map<NotificationType, Boolean> defaults = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            defaults.put(type, true);
        }
        return NotificationPreferencesDto.builder()
                .userId(userId)
                .preferences(defaults)
                .build();
    }

    public boolean isEnabled(NotificationType type) {
        return preferences != null && Boolean.TRUE.equals(preferences.getOrDefault(type, true));
    }
}
