package com.safalifter.jobservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.Collections;
import java.util.Date;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.safalifter.jobservice.service.JobService;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private Key signKey;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private String createToken(String username, String role, long expirationMs) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createTokenWithoutIssuer(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createTokenWithoutSubject(String role) {
        return Jwts.builder()
                .setIssuer(role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("ADMIN 访问 admin 接口 (getAll jobs) 应该返回 200 OK")
    void adminAccessAdminEndpoint_shouldSucceed() throws Exception {
        String adminToken = createToken("admin", "ROLE_ADMIN", 3600000);

        when(jobService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("USER 访问公共接口 (getAll jobs) 应该返回 200 OK")
    void userAccessPublicEndpoint_shouldSucceed() throws Exception {
        String userToken = createToken("regularuser", "ROLE_USER", 3600000);

        when(jobService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无 Token 访问公共接口 应该返回 200 OK")
    void noTokenAccessPublicEndpoint_shouldSucceed() throws Exception {
        when(jobService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("过期 Token 应该返回 401 Unauthorized")
    void expiredToken_shouldReturn401() throws Exception {
        String expiredToken = createToken("admin", "ROLE_ADMIN", -1000);

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expired"));
    }

    @Test
    @DisplayName("无效 Token (签名错误) 应该返回 401 Unauthorized")
    void invalidToken_shouldReturn401() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6IlJPTEVfQURNSU4ifQ.invalid_signature";

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + invalidToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺少 issuer (角色) 的 Token 应该返回 401 Unauthorized")
    void tokenWithoutRole_shouldReturn401() throws Exception {
        String tokenWithoutRole = createTokenWithoutIssuer("admin");

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + tokenWithoutRole)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid token: missing required claims"));
    }

    @Test
    @DisplayName("缺少 subject (用户名) 的 Token 应该返回 401 Unauthorized")
    void tokenWithoutSubject_shouldReturn401() throws Exception {
        String tokenWithoutSubject = createTokenWithoutSubject("ROLE_ADMIN");

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "Bearer " + tokenWithoutSubject)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid token: missing required claims"));
    }

    @Test
    @DisplayName("格式错误的 Authorization header 应该继续执行 (匿名访问公共接口)")
    void malformedAuthHeader_shouldContinueToPublicEndpoint() throws Exception {
        when(jobService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/job-service/job/getAll")
                        .header("Authorization", "InvalidFormat")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
