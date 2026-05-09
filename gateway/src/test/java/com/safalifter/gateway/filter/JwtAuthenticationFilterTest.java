package com.safalifter.gateway.filter;

import com.safalifter.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;

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
}
