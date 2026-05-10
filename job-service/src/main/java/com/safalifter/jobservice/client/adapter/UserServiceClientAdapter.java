package com.safalifter.jobservice.client.adapter;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.exc.UnauthorizedException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientAdapter {

    private final UserServiceClient userServiceClient;

    public UserDto getUserByIdOrThrow(String id) {
        try {
            ResponseEntity<UserDto> response = userServiceClient.getUserById(id);
            UserDto user = extractBodyOrThrow(response, "User not found: " + id);
            if (user.getId() == null) {
                throw new NotFoundException("User not found: " + id);
            }
            return user;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("User not found: " + id);
        } catch (FeignException e) {
            log.error("Failed to get user by id: {} (status={})", id, e.status(), e);
            throw new UnauthorizedException("Failed to get user: " + id);
        }
    }

    public UserDto getUserInfoByUsernameOrThrow(String username) {
        try {
            ResponseEntity<UserDto> response = userServiceClient.getUserInfoByUsername(username);
            UserDto user = extractBodyOrThrow(response, "User not found: " + username);
            if (user.getId() == null) {
                throw new NotFoundException("User not found: " + username);
            }
            return user;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("User not found: " + username);
        } catch (FeignException e) {
            log.error("Failed to get user by username: {} (status={})", username, e.status(), e);
            throw new UnauthorizedException("Failed to validate user: " + username);
        }
    }

    public NotificationPreferencesDto getNotificationPreferencesOrDefault(String userId) {
        try {
            ResponseEntity<NotificationPreferencesDto> response = userServiceClient.getNotificationPreferences(userId);
            NotificationPreferencesDto prefs = extractBodyOrNull(response);
            if (prefs == null || prefs.getPreferences() == null) {
                log.warn("Notification preferences not found for userId: {}, using default", userId);
                return NotificationPreferencesDto.createDefault(userId);
            }
            return prefs;
        } catch (FeignException.NotFound e) {
            log.warn("Notification preferences not found for userId: {}, using default", userId);
            return NotificationPreferencesDto.createDefault(userId);
        } catch (FeignException e) {
            log.error("Failed to get notification preferences for userId: {} (status={})", userId, e.status(), e);
            return NotificationPreferencesDto.createDefault(userId);
        }
    }

    private <T> T extractBodyOrThrow(ResponseEntity<T> response, String notFoundMessage) {
        if (response == null) {
            throw new NotFoundException(notFoundMessage);
        }
        T body = response.getBody();
        if (body == null) {
            throw new NotFoundException(notFoundMessage);
        }
        return body;
    }

    private <T> T extractBodyOrNull(ResponseEntity<T> response) {
        return response != null ? response.getBody() : null;
    }
}
