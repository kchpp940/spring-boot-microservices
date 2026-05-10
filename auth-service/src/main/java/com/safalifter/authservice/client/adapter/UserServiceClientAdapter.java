package com.safalifter.authservice.client.adapter;

import com.safalifter.authservice.client.UserServiceClient;
import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.dto.UserDto;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.request.RegisterRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientAdapter {

    private final UserServiceClient userServiceClient;

    public RegisterDto save(RegisterRequest request) {
        try {
            ResponseEntity<RegisterDto> response = userServiceClient.save(request);
            if (response == null || response.getBody() == null) {
                throw GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Failed to create user: empty response from user-service")
                        .build();
            }
            return response.getBody();
        } catch (GenericErrorResponse e) {
            log.info("User-service returned business error: status={}, message={}",
                    e.getHttpStatus(), e.getMessage());
            throw e;
        } catch (FeignException e) {
            log.error("Feign error calling user-service: status={}, message={}",
                    e.status(), e.getMessage());
            HttpStatus status = HttpStatus.resolve(e.status());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            throw GenericErrorResponse.builder()
                    .httpStatus(status)
                    .message("Service unavailable: user-service - " + e.getMessage())
                    .build();
        }
    }

    public UserDto getUserByUsername(String username) {
        try {
            ResponseEntity<UserDto> response = userServiceClient.getUserByUsername(username);
            if (response == null || response.getBody() == null) {
                throw GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .message("User not found: " + username)
                        .build();
            }
            return response.getBody();
        } catch (GenericErrorResponse e) {
            throw e;
        } catch (FeignException e) {
            log.error("Feign error calling user-service for username={}: status={}, message={}",
                    username, e.status(), e.getMessage());
            HttpStatus status = HttpStatus.resolve(e.status());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            throw GenericErrorResponse.builder()
                    .httpStatus(status)
                    .message("Service unavailable: user-service - " + e.getMessage())
                    .build();
        }
    }

    public void internalHardDeleteUserById(String id) {
        try {
            userServiceClient.internalHardDeleteUserById(id);
        } catch (FeignException e) {
            log.error("Failed to hard delete user by id={}: status={}, message={}",
                    id, e.status(), e.getMessage());
            HttpStatus status = HttpStatus.resolve(e.status());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            throw GenericErrorResponse.builder()
                    .httpStatus(status)
                    .message("Service unavailable: user-service - " + e.getMessage())
                    .build();
        }
    }
}
