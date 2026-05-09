package com.safalifter.userservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.userservice.exc.DuplicateResourceException;
import com.safalifter.userservice.exc.GeneralExceptionHandler;
import com.safalifter.userservice.model.User;
import com.safalifter.userservice.repository.UserRepository;
import com.safalifter.userservice.request.RegisterRequest;
import com.safalifter.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Service层: 重复的 username 应该抛出 DuplicateResourceException")
    void duplicateUsername_shouldThrowDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setPassword("Password123");
        request.setEmail("newemail@example.com");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.saveUser(request)
        );

        assertTrue(exception.getMessage().contains("username"));
        assertTrue(exception.getMessage().contains("existinguser"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Service层: 重复的 email 应该抛出 DuplicateResourceException")
    void duplicateEmail_shouldThrowDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("existing@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.saveUser(request)
        );

        assertTrue(exception.getMessage().contains("email"));
        assertTrue(exception.getMessage().contains("existing@example.com"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Controller层: 重复 username 应该返回 409 CONFLICT")
    void duplicateUsername_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setPassword("Password123");
        request.setEmail("newemail@example.com");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("username already exists: existinguser"))
                .andExpect(jsonPath("$.field").value("username"))
                .andExpect(jsonPath("$.value").value("existinguser"));
    }

    @Test
    @DisplayName("Controller层: 重复 email 应该返回 409 CONFLICT")
    void duplicateEmail_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("existing@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.field").value("email"))
                .andExpect(jsonPath("$.value").value("existing@example.com"));
    }

    @Test
    @DisplayName("Controller层: 无效参数应该返回 400 BAD_REQUEST")
    void invalidRequest_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("short");
        request.setEmail("invalid-email");

        mockMvc.perform(post("/v1/user/save")
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

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controller层: 数据库唯一约束异常 (username) 应该返回 409 CONFLICT")
    void dataIntegrityViolationForUsername_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("new@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index or primary key violation: \"CONSTRAINT_INDEX_2 ON PUBLIC.USERS(USERNAME) VALUES ('newuser', 1)\""));

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.field").value("username"));
    }

    @Test
    @DisplayName("Controller层: 数据库唯一约束异常 (email) 应该返回 409 CONFLICT")
    void dataIntegrityViolationForEmail_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("existing@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index or primary key violation: \"CONSTRAINT_INDEX_4 ON PUBLIC.USERS(EMAIL) VALUES ('existing@example.com', 1)\""));

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    @DisplayName("Controller层: PostgreSQL 风格 username 唯一约束应该返回 409 CONFLICT")
    void dataIntegrityViolationPostgresUsername_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("new@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"uk_username\" Detail: Key (username)=(existinguser) already exists."));

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.field").value("username"));
    }

    @Test
    @DisplayName("Controller层: PostgreSQL 风格 email 唯一约束应该返回 409 CONFLICT")
    void dataIntegrityViolationPostgresEmail_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("new@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"uk_email\" Detail: Key (email)=(existing@example.com) already exists."));

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    @DisplayName("Controller层: 无法识别的唯一约束也应该稳定返回 409 CONFLICT")
    void dataIntegrityViolationUnrecognized_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("new@example.com");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Some generic constraint violation message without field info"));

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Data integrity violation: duplicate resource"));
    }

    @Test
    @DisplayName("Controller层: email 为空白字符串应该返回 400 BAD_REQUEST")
    void blankEmail_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("Password123");
        request.setEmail("   ");

        mockMvc.perform(post("/v1/user/save")
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

        mockMvc.perform(post("/v1/user/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
