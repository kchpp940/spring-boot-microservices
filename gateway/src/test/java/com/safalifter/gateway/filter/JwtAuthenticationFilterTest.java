package com.safalifter.gateway.filter;

import com.safalifter.gateway.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private Key signKey;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private String createValidToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createExpiredToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(role)
                .setIssuedAt(new Date(System.currentTimeMillis() - 3600000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createTokenWithWrongSignature(String username, String role) {
        String differentSecret = "4267566B59703373367639792F423F4528482B4D6251655468576D5A71347436";
        Key differentKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentSecret));
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private GatewayFilterChain createChainThatChecksHeaderPresence(String expectedToken, AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            HttpHeaders headers = exchange.getRequest().getHeaders();
            assertTrue(headers.containsKey(HttpHeaders.AUTHORIZATION),
                    "Authorization header should still be present");
            assertTrue(headers.getOrEmpty(HttpHeaders.AUTHORIZATION).get(0).startsWith("Bearer "),
                    "Authorization header should start with Bearer");
            return Mono.empty();
        };
    }

    private GatewayFilterChain createChainThatChecksHeaderUnchanged(String expectedToken, AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String authHeader = headers.getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
            assertEquals(expectedToken, authHeader,
                    "Authorization header should be unchanged");
            return Mono.empty();
        };
    }

    private GatewayFilterChain createChainThatPasses(AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }

    private GatewayFilterChain createChainThatFails() {
        return exchange -> {
            fail("Should not reach downstream service");
            return Mono.empty();
        };
    }

    @Test
    @DisplayName("验证 Gateway 过滤器不会移除 Authorization header")
    void filter_withValidToken_shouldNotRemoveAuthorizationHeader() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String expectedAuthHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatChecksHeaderPresence(expectedAuthHeader, chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called");
    }

    @Test
    @DisplayName("验证 Gateway 过滤器只验证 token，不解析角色")
    void filter_shouldOnlyValidateTokenNotParseRoles() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String expectedAuthHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatChecksHeaderUnchanged(expectedAuthHeader, chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called");
    }

    @Test
    @DisplayName("验证公共端点 (login) 不需要 Authorization header")
    void filter_publicEndpoint_shouldNotRequireAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/auth/login")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, createChainThatPasses(chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called for public endpoint");
        assertEquals("/v1/auth/login", exchange.getRequest().getURI().getPath());
    }

    @Test
    @DisplayName("验证受保护端点缺少 Authorization header 时返回 401")
    void filter_protectedEndpointWithoutAuthHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证无效 token 时返回 401")
    void filter_withInvalidToken_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new RuntimeException("Invalid token")).when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证过期 token 时返回 401")
    void filter_withExpiredToken_shouldReturn401() {
        String expiredToken = createExpiredToken("admin", "ROLE_ADMIN");
        String authHeader = "Bearer " + expiredToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new ExpiredJwtException(null, null, "Token expired")).when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证签名错误的 token 时返回 401")
    void filter_withWrongSignatureToken_shouldReturn401() {
        String wrongSigToken = createTokenWithWrongSignature("admin", "ROLE_ADMIN");
        String authHeader = "Bearer " + wrongSigToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new SignatureException("JWT signature does not match")).when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证角色不匹配时 Gateway 不拦截，请求透传至后端服务")
    void filter_withRoleMismatch_shouldPassToBackend() {
        String userToken = createValidToken("user", "ROLE_USER");
        String authHeader = "Bearer " + userToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatChecksHeaderPresence(authHeader, chainCalled)).block();

        assertTrue(chainCalled.get(), "Gateway should pass request to backend for role validation");
    }

    @Test
    @DisplayName("验证公共端点 register 不需要 Authorization header")
    void filter_registerEndpoint_shouldNotRequireAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/auth/register")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, createChainThatPasses(chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called for public endpoint");
        assertEquals("/v1/auth/register", exchange.getRequest().getURI().getPath());
    }

    @Test
    @DisplayName("验证路由到 user-service 时 Authorization header 保持不变")
    void filter_toUserService_shouldPreserveAuthorizationHeader() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String expectedAuthHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatChecksHeaderUnchanged(expectedAuthHeader, chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called for user-service");
    }

    @Test
    @DisplayName("验证路由到 job-service 时 Authorization header 保持不变")
    void filter_toJobService_shouldPreserveAuthorizationHeader() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String expectedAuthHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/job-service/job/getAll")
                .header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatChecksHeaderUnchanged(expectedAuthHeader, chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called for job-service");
    }

    @Test
    @DisplayName("验证路由到 auth-service 公共端点不需要 Authorization")
    void filter_toAuthServicePublicEndpoint_shouldNotRequireAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/auth/login")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, createChainThatPasses(chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called for auth-service public endpoint");
    }

    @Test
    @DisplayName("验证 Authorization header 格式错误时返回 401")
    void filter_withMalformedAuthorizationHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, "invalid-format-token")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new MalformedJwtException("Invalid JWT format")).when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证 Authorization header 为空时返回 401")
    void filter_withEmptyAuthorizationHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, "")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new MalformedJwtException("Empty token")).when(jwtUtil).validateToken("");

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证只有 Bearer 前缀没有 token 时返回 401")
    void filter_withOnlyBearerPrefix_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        doThrow(new MalformedJwtException("Empty token")).when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatFails()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("验证受保护接口透传用户身份 - 自定义请求头也被保留")
    void filter_protectedEndpoint_shouldPassAllHeaders() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String expectedAuthHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)
                .header("X-Custom-Header", "custom-value")
                .header("X-Request-Id", "12345")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        GatewayFilterChain chain = e -> {
            chainCalled.set(true);
            HttpHeaders headers = e.getRequest().getHeaders();
            assertTrue(headers.containsKey(HttpHeaders.AUTHORIZATION));
            assertTrue(headers.containsKey("X-Custom-Header"));
            assertTrue(headers.containsKey("X-Request-Id"));
            assertEquals("custom-value", headers.getFirst("X-Custom-Header"));
            assertEquals("12345", headers.getFirst("X-Request-Id"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Chain should have been called");
    }

    @Test
    @DisplayName("验证 token 验证成功后调用链，确保 gateway 不吞掉请求")
    void filter_validToken_shouldCallChain() {
        String validToken = createValidToken("admin", "ROLE_ADMIN");
        String authHeader = "Bearer " + validToken;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/user/getAll")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        doNothing().when(jwtUtil).validateToken(anyString());

        filter.filter(exchange, createChainThatPasses(chainCalled)).block();

        assertTrue(chainCalled.get(), "Chain should have been called");
        verify(jwtUtil, times(1)).validateToken(anyString());
    }
}
