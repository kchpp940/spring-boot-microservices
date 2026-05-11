package com.safalifter.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.security.Key;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.discovery.locator.enabled=false",
                "eureka.client.enabled=false"
        })
class GatewayRouteBoundaryTest {

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private static Key signKey;
    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        String baseUrl = "http://localhost:" + wireMockServer.port();
        registry.add("gateway.routes.user-service.uri", () -> baseUrl);
        registry.add("gateway.routes.job-service.uri", () -> baseUrl);
        registry.add("gateway.routes.auth-service.uri", () -> baseUrl);
        registry.add("gateway.routes.notification-service.uri", () -> baseUrl);
        registry.add("gateway.routes.file-storage.uri", () -> baseUrl);
    }

    @BeforeAll
    static void setUpAll() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        signKey = Keys.hmacShaKeyFor(keyBytes);
        WireMock.configureFor(wireMockServer.port());
    }

    @AfterEach
    void tearDown() {
        WireMock.reset();
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
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

    @Nested
    @DisplayName("Auth Service 路由测试 - 公开接口")
    class AuthServiceRouteTests {

        @Test
        @DisplayName("POST /v1/auth/login 应路由到 auth-service 并返回 200")
        void login_shouldRouteToAuthService_andReturn200() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("X-Auth-Service", "true")
                            .withBody("{\"token\":\"test-token\",\"expiresIn\":3600}")));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-Auth-Service", "true")
                    .expectBody()
                    .jsonPath("$.token").isEqualTo("test-token");
        }

        @Test
        @DisplayName("POST /v1/auth/register 应路由到 auth-service 并返回 200")
        void register_shouldRouteToAuthService_andReturn200() {
            stubFor(post(urlEqualTo("/v1/auth/register"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"id\":\"123\",\"username\":\"newuser\"}")));

            webTestClient.post().uri("/v1/auth/register")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"newuser\",\"password\":\"password\",\"email\":\"test@test.com\"}")
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo("newuser");
        }

        @Test
        @DisplayName("POST /v1/auth/login 无需 Authorization header (公开接口)")
        void login_shouldNotRequireAuthHeader() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse().withStatus(200)));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("auth-service 返回 401 应原样透传")
        void authServiceReturns401_shouldPassThrough() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Bearer")
                            .withBody("{\"error\":\"Invalid credentials\"}")));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"wrong\",\"password\":\"wrong\"}")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Invalid credentials");
        }

        @Test
        @DisplayName("auth-service 返回 500 应原样透传")
        void authServiceReturns500_shouldPassThrough() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("X-Error-Type", "Internal")
                            .withBody("{\"error\":\"Internal server error\"}")));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().valueEquals("X-Error-Type", "Internal")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Internal server error");
        }
    }

    @Nested
    @DisplayName("User Service 路由测试 - 受保护接口")
    class UserServiceRouteTests {

        @Test
        @DisplayName("GET /v1/user/getAll 无 token 应返回 401 (gateway 层面)")
        void getAll_withoutToken_shouldReturn401FromGateway() {
            webTestClient.get().uri("/v1/user/getAll")
                    .exchange()
                    .expectStatus().isUnauthorized();

            WireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        }

        @Test
        @DisplayName("GET /v1/user/getAll 有效 token 应路由到 user-service")
        void getAll_withValidToken_shouldRouteToUserService() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("X-User-Service", "true")
                            .withBody("[{\"id\":\"1\",\"username\":\"admin\"}]")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-User-Service", "true")
                    .expectBody()
                    .jsonPath("$[0].username").isEqualTo("admin");
        }

        @Test
        @DisplayName("user-service 返回 403 (角色不匹配) 应原样透传")
        void userServiceReturns403_shouldPassThrough() {
            String token = createValidToken("user", "ROLE_USER");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .willReturn(aResponse()
                            .withStatus(403)
                            .withHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\"")
                            .withBody("{\"error\":\"Access denied\"}")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"insufficient_scope\"")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Access denied");
        }

        @Test
        @DisplayName("user-service 返回 404 应原样透传")
        void userServiceReturns404_shouldPassThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getUserById/999"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("X-Error-Code", "USER_NOT_FOUND")
                            .withBody("{\"error\":\"User not found\"}")));

            webTestClient.get().uri("/v1/user/getUserById/999")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().valueEquals("X-Error-Code", "USER_NOT_FOUND")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("User not found");
        }

        @Test
        @DisplayName("user-service 返回 500 应原样透传")
        void userServiceReturns500_shouldPassThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("X-Error-Type", "Database")
                            .withBody("{\"error\":\"Database error\"}")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().valueEquals("X-Error-Type", "Database")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Database error");
        }

        @Test
        @DisplayName("Authorization header 应原样透传给 user-service")
        void authorizationHeader_shouldBePassedToUserService() {
            String token = createValidToken("admin", "ROLE_ADMIN");
            String authHeader = "Bearer " + token;

            stubFor(get(urlEqualTo("/v1/user/getUserById/123"))
                    .withHeader("Authorization", equalTo(authHeader))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"id\":\"123\",\"username\":\"admin\"}")));

            webTestClient.get().uri("/v1/user/getUserById/123")
                    .header("Authorization", authHeader)
                    .header("X-Request-Id", "req-12345")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("自定义请求头应透传给 user-service")
        void customHeaders_shouldBePassedToUserService() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .withHeader("X-Request-Id", equalTo("trace-789"))
                    .withHeader("X-Correlation-Id", equalTo("corr-456"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("[]")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "trace-789")
                    .header("X-Correlation-Id", "corr-456")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Job Service 路由测试 - 受保护接口")
    class JobServiceRouteTests {

        @Test
        @DisplayName("GET /v1/job-service/job/getAll 无 token 应返回 401 (gateway 层面)")
        void getAll_withoutToken_shouldReturn401FromGateway() {
            webTestClient.get().uri("/v1/job-service/job/getAll")
                    .exchange()
                    .expectStatus().isUnauthorized();

            WireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        }

        @Test
        @DisplayName("GET /v1/job-service/job/getAll 有效 token 应路由到 job-service")
        void getAll_withValidToken_shouldRouteToJobService() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/job-service/job/getAll"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("X-Job-Service", "true")
                            .withBody("[{\"id\":\"1\",\"title\":\"Software Engineer\"}]")));

            webTestClient.get().uri("/v1/job-service/job/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-Job-Service", "true")
                    .expectBody()
                    .jsonPath("$[0].title").isEqualTo("Software Engineer");
        }

        @Test
        @DisplayName("job-service 返回 400 应原样透传")
        void jobServiceReturns400_shouldPassThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlPathEqualTo("/v1/job-service/job/getJobById/invalid"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withHeader("X-Error-Code", "INVALID_ID")
                            .withBody("{\"error\":\"Invalid job ID\"}")));

            webTestClient.get().uri("/v1/job-service/job/getJobById/invalid")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().valueEquals("X-Error-Code", "INVALID_ID")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Invalid job ID");
        }

        @Test
        @DisplayName("job-service 返回 403 (角色不匹配) 应原样透传")
        void jobServiceReturns403_shouldPassThrough() {
            String token = createValidToken("user", "ROLE_USER");

            stubFor(post(urlPathEqualTo("/v1/job-service/job/create"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .willReturn(aResponse()
                            .withStatus(403)
                            .withBody("{\"error\":\"Only ADMIN can create jobs\"}")));

            webTestClient.post().uri("/v1/job-service/job/create")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Only ADMIN can create jobs");
        }

        @Test
        @DisplayName("job-service 返回 422 应原样透传")
        void jobServiceReturns422_shouldPassThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(post(urlPathEqualTo("/v1/job-service/job/create"))
                    .willReturn(aResponse()
                            .withStatus(422)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("{\"title\":\"Validation failed\",\"detail\":\"Title is required\"}")));

            webTestClient.post().uri("/v1/job-service/job/create")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(422)
                    .expectHeader().contentType("application/problem+json")
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Validation failed");
        }

        @Test
        @DisplayName("job-service 返回 503 应原样透传")
        void jobServiceReturns503_shouldPassThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlPathEqualTo("/v1/job-service/job/getAll"))
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withHeader("Retry-After", "10")
                            .withBody("{\"error\":\"Service unavailable\"}")));

            webTestClient.get().uri("/v1/job-service/job/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader().valueEquals("Retry-After", "10")
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("Service unavailable");
        }

        @Test
        @DisplayName("Authorization header 应原样透传给 job-service")
        void authorizationHeader_shouldBePassedToJobService() {
            String token = createValidToken("admin", "ROLE_ADMIN");
            String authHeader = "Bearer " + token;

            stubFor(get(urlPathEqualTo("/v1/job-service/job/getJobById/456"))
                    .withHeader("Authorization", equalTo(authHeader))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("{\"id\":\"456\",\"title\":\"QA Engineer\"}")));

            webTestClient.get().uri("/v1/job-service/job/getJobById/456")
                    .header("Authorization", authHeader)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("路由边界测试")
    class RouteBoundaryTests {

        @Test
        @DisplayName("公开接口 (auth) 和受保护接口 (user) 行为差异")
        void publicVsProtectedEndpoints_shouldHaveDifferentBehavior() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse().withStatus(200)));
            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse().withStatus(200)));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isOk();

            webTestClient.get().uri("/v1/user/getAll")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("多个服务的 401 响应格式一致性")
        void multipleServices_401ResponseConsistency() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Bearer")
                            .withBody("{\"error\":\"Unauthorized\"}")));

            String token = createValidToken("user", "ROLE_USER");
            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Bearer")
                            .withBody("{\"error\":\"Unauthorized\"}")));

            stubFor(get(urlPathEqualTo("/v1/job-service/job/getAll"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Bearer")
                            .withBody("{\"error\":\"Unauthorized\"}")));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists("WWW-Authenticate");

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists("WWW-Authenticate");

            webTestClient.get().uri("/v1/job-service/job/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists("WWW-Authenticate");
        }

        @Test
        @DisplayName("多个服务的 500 响应格式一致性")
        void multipleServices_500ResponseConsistency() {
            stubFor(post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"Internal server error\"}")));

            String token = createValidToken("admin", "ROLE_ADMIN");
            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"Internal server error\"}")));

            stubFor(get(urlPathEqualTo("/v1/job-service/job/getAll"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"Internal server error\"}")));

            webTestClient.post().uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"username\":\"admin\",\"password\":\"admin\"}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().contentType("application/json");

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().contentType("application/json");

            webTestClient.get().uri("/v1/job-service/job/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().contentType("application/json");
        }
    }

    @Nested
    @DisplayName("响应头透传测试")
    class ResponseHeaderPassthroughTests {

        @Test
        @DisplayName("下游服务的响应头应原样透传")
        void downstreamResponseHeaders_shouldBePassedThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json;charset=UTF-8")
                            .withHeader("X-Total-Count", "100")
                            .withHeader("X-Page-Count", "10")
                            .withHeader("Cache-Control", "max-age=3600")
                            .withBody("[]")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-Total-Count", "100")
                    .expectHeader().valueEquals("X-Page-Count", "10")
                    .expectHeader().valueEquals("Cache-Control", "max-age=3600");
        }

        @Test
        @DisplayName("错误响应的头也应透传")
        void errorResponseHeaders_shouldBePassedThrough() {
            String token = createValidToken("admin", "ROLE_ADMIN");

            stubFor(get(urlEqualTo("/v1/user/getAll"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("X-Error-Code", "DB-001")
                            .withHeader("X-Retry-After", "5")
                            .withBody("{\"error\":\"Database connection failed\"}")));

            webTestClient.get().uri("/v1/user/getAll")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().valueEquals("X-Error-Code", "DB-001")
                    .expectHeader().valueEquals("X-Retry-After", "5");
        }
    }
}
