package com.safalifter.authservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.authservice.client.NotificationServiceClient;
import com.safalifter.authservice.client.UserServiceClient;
import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.exc.ValidationException;
import com.safalifter.authservice.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRegistrationExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private NotificationServiceClient notificationServiceClient;

    @Test
    @DisplayName("Controller层: 无效参数应该返回 400 BAD_REQUEST")
    void invalidRequest_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("short");
        request.setEmail("invalid-email");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.password").exists())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    @DisplayName("Controller层: 缺少必填字段应该返回 400 BAD_REQUEST")
    void missingRequiredFields_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controller层: email 为空白字符串应该返回 400 BAD_REQUEST")
    void blankEmail_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("   ");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    @DisplayName("Controller层: email 为 null 应该返回 400 BAD_REQUEST")
    void nullEmail_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controller层: 重复 username 从 user-service 返回 409 应该正确转换")
    void duplicateUsernameFromUserService_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setPassword("Password123");
        request.setEmail("newemail@example.com");

        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", "username already exists: existinguser");
        errorBody.put("field", "username");
        errorBody.put("value", "existinguser");

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.CONFLICT)
                        .message("username already exists: existinguser")
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("username already exists: existinguser"));
    }

    @Test
    @DisplayName("Controller层: 重复 email 从 user-service 返回 409 应该正确转换")
    void duplicateEmailFromUserService_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("existing@example.com");

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.CONFLICT)
                        .message("email already exists: existing@example.com")
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("email already exists: existing@example.com"));
    }

    @Test
    @DisplayName("Controller层: user-service 验证错误 (400) 应该正确转换为 ValidationException")
    void userServiceValidationError_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("valid@example.com");

        Map<String, String> validationErrors = new HashMap<>();
        validationErrors.put("email", "Email should be valid");

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenThrow(ValidationException.builder()
                        .validationErrors(validationErrors)
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.email").value("Email should be valid"));
    }

    @Test
    @DisplayName("Controller层: 成功注册应该返回 200 OK 并发送欢迎通知")
    void validRequest_shouldReturn200AndSendWelcomeNotification() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("valid@example.com");

        RegisterDto registerDto = RegisterDto.builder()
                .id("user-123")
                .username("validuser")
                .email("valid@example.com")
                .build();

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenReturn(ResponseEntity.ok(registerDto));
        when(notificationServiceClient.save(any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.username").value("validuser"))
                .andExpect(jsonPath("$.email").value("valid@example.com"));

        verify(userServiceClient, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClient, times(1)).save(any());
        verify(userServiceClient, never()).internalHardDeleteUserById(anyString());
    }

    @Test
    @DisplayName("Orchestration: notification-service 失败时应该回滚 user-service 创建的用户")
    void notificationServiceFailure_shouldRollbackUser() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Password123");
        request.setEmail("test@example.com");

        RegisterDto registerDto = RegisterDto.builder()
                .id("user-456")
                .username("testuser")
                .email("test@example.com")
                .build();

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenReturn(ResponseEntity.ok(registerDto));
        when(notificationServiceClient.save(any()))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Notification service failed")
                        .build());
        when(userServiceClient.internalHardDeleteUserById("user-456"))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(userServiceClient, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClient, times(1)).save(any());
        verify(userServiceClient, times(1)).internalHardDeleteUserById("user-456");
    }

    @Test
    @DisplayName("Orchestration: user-service 成功但 notification 失败且回滚也失败时返回明确错误")
    void rollbackFailure_shouldReturnClearError() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("rollbackfail");
        request.setPassword("Password123");
        request.setEmail("rollback@example.com");

        RegisterDto registerDto = RegisterDto.builder()
                .id("user-789")
                .username("rollbackfail")
                .email("rollback@example.com")
                .build();

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenReturn(ResponseEntity.ok(registerDto));
        when(notificationServiceClient.save(any()))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .message("Notification service unavailable")
                        .build());
        when(userServiceClient.internalHardDeleteUserById("user-789"))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Rollback failed")
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(userServiceClient, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClient, times(1)).save(any());
        verify(userServiceClient, times(1)).internalHardDeleteUserById("user-789");
    }

    @Test
    @DisplayName("Controller层: user-service 服务不可用 (503) 应该返回 503")
    void userServiceUnavailable_shouldReturn503() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("valid@example.com");

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .message("Service Unavailable")
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());

        verify(userServiceClient, never()).internalHardDeleteUserById(anyString());
    }

    @Test
    @DisplayName("Controller层: Feign 连接失败 (500) 应该返回 500")
    void feignConnectionFailure_shouldReturn500() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("valid@example.com");

        when(userServiceClient.save(any(RegisterRequest.class)))
                .thenThrow(GenericErrorResponse.builder()
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Connection refused: user-service")
                        .build());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Connection refused: user-service"));
    }
}
