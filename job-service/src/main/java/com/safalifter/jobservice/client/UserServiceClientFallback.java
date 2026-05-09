package com.safalifter.jobservice.client;

import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ResponseEntity<UserDto> getUserById(String id) {
        log.warn("Fallback triggered for getUserById with id: {}", id);
        return null;
    }

    @Override
    public ResponseEntity<NotificationPreferencesDto> getNotificationPreferences(String userId) {
        log.warn("Fallback triggered for getNotificationPreferences with userId: {}, returning default preferences", userId);
        return ResponseEntity.ok(NotificationPreferencesDto.createDefault(userId));
    }
}
