package com.safalifter.authservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.exc.ValidationException;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomErrorDecoder 契约测试 - auth-service")
class CustomErrorDecoderContractTest {

    private CustomErrorDecoder errorDecoder;
    private ObjectMapper objectMapper;
    private static final String TEST_METHOD_KEY = "UserServiceClient#save(RegisterRequest)";

    @BeforeEach
    void setUp() {
        errorDecoder = new CustomErrorDecoder();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("400 BAD_REQUEST - 业务/验证错误")
    class BadRequestTests {

        @Test
        @DisplayName("400 - 验证错误格式应映射为 ValidationException")
        void decode_400ValidationError_shouldReturnValidationException() throws IOException {
            Map<String, Object> validationErrors = new HashMap<>();
            validationErrors.put("username", "Username must not be blank");
            validationErrors.put("email", "Email should be valid");
            validationErrors.put("password", "Password must be at least 8 characters");

            Response response = buildResponse(400, validationErrors);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(ValidationException.class, exception);
            ValidationException ve = (ValidationException) exception;
            assertNotNull(ve.getValidationErrors());
            assertEquals(3, ve.getValidationErrors().size());
            assertEquals("Username must not be blank", ve.getValidationErrors().get("username"));
            assertEquals("Email should be valid", ve.getValidationErrors().get("email"));
            assertEquals("Password must be at least 8 characters", ve.getValidationErrors().get("password"));
        }

        @Test
        @DisplayName("400 - 含 error 字段应映射为 GenericErrorResponse")
        void decode_400WithErrorField_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Invalid request: missing required field");
            errorBody.put("field", "userId");

            Response response = buildResponse(400, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.BAD_REQUEST, ger.getHttpStatus());
            assertEquals("Invalid request: missing required field", ger.getMessage());
        }

        @Test
        @DisplayName("400 - 空响应体应映射为 GenericErrorResponse (reason phrase)")
        void decode_400EmptyBody_shouldReturnGenericErrorResponseWithReasonPhrase() throws IOException {
            Response response = buildResponse(400, new HashMap<>());

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.BAD_REQUEST, ger.getHttpStatus());
            assertEquals("Bad Request", ger.getMessage());
        }

        @Test
        @DisplayName("400 - 非字符串 error 字段应使用 reason phrase")
        void decode_400NonStringError_shouldUseReasonPhrase() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", 12345);

            Response response = buildResponse(400, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.BAD_REQUEST, ger.getHttpStatus());
            assertEquals("Bad Request", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("401 UNAUTHORIZED - 权限错误 (未认证)")
    class UnauthorizedTests {

        @Test
        @DisplayName("401 - 应映射为 GenericErrorResponse")
        void decode_401_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Full authentication is required to access this resource");

            Response response = buildResponse(401, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.UNAUTHORIZED, ger.getHttpStatus());
            assertEquals("Full authentication is required to access this resource", ger.getMessage());
        }

        @Test
        @DisplayName("401 - 无 error 字段应使用 reason phrase")
        void decode_401EmptyBody_shouldUseReasonPhrase() throws IOException {
            Response response = buildResponse(401, null);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.UNAUTHORIZED, ger.getHttpStatus());
            assertEquals("Service error: Unauthorized", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("403 FORBIDDEN - 权限错误 (已认证但无权限)")
    class ForbiddenTests {

        @Test
        @DisplayName("403 - 应映射为 GenericErrorResponse")
        void decode_403_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Access denied: insufficient permissions");

            Response response = buildResponse(403, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.FORBIDDEN, ger.getHttpStatus());
            assertEquals("Access denied: insufficient permissions", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("404 NOT_FOUND - 资源不存在")
    class NotFoundTests {

        @Test
        @DisplayName("404 - 应映射为 GenericErrorResponse")
        void decode_404_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Resource not found: user-123");
            errorBody.put("resource", "user");
            errorBody.put("id", "user-123");

            Response response = buildResponse(404, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.NOT_FOUND, ger.getHttpStatus());
            assertEquals("Resource not found: user-123", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("409 CONFLICT - 资源冲突")
    class ConflictTests {

        @Test
        @DisplayName("409 - 用户名重复应映射为 GenericErrorResponse")
        void decode_409DuplicateUsername_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "username already exists: existinguser");
            errorBody.put("field", "username");
            errorBody.put("value", "existinguser");

            Response response = buildResponse(409, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.CONFLICT, ger.getHttpStatus());
            assertEquals("username already exists: existinguser", ger.getMessage());
        }

        @Test
        @DisplayName("409 - 邮箱重复应映射为 GenericErrorResponse")
        void decode_409DuplicateEmail_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "email already exists: existing@example.com");
            errorBody.put("field", "email");

            Response response = buildResponse(409, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.CONFLICT, ger.getHttpStatus());
            assertEquals("email already exists: existing@example.com", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("5xx 服务器错误 - 下游故障")
    class ServerErrorTests {

        @Test
        @DisplayName("500 INTERNAL_SERVER_ERROR - 应映射为 GenericErrorResponse")
        void decode_500_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Internal server error occurred");

            Response response = buildResponse(500, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ger.getHttpStatus());
            assertEquals("Internal server error occurred", ger.getMessage());
        }

        @Test
        @DisplayName("502 BAD_GATEWAY - 应映射为 GenericErrorResponse")
        void decode_502_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Bad gateway: upstream service returned invalid response");

            Response response = buildResponse(502, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.BAD_GATEWAY, ger.getHttpStatus());
            assertEquals("Bad gateway: upstream service returned invalid response", ger.getMessage());
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE - 应映射为 GenericErrorResponse")
        void decode_503_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Service unavailable: user-service is under maintenance");

            Response response = buildResponse(503, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ger.getHttpStatus());
            assertEquals("Service unavailable: user-service is under maintenance", ger.getMessage());
        }

        @Test
        @DisplayName("504 GATEWAY_TIMEOUT - 应映射为 GenericErrorResponse")
        void decode_504_shouldReturnGenericErrorResponse() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Gateway timeout: upstream service did not respond in time");

            Response response = buildResponse(504, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, ger.getHttpStatus());
            assertEquals("Gateway timeout: upstream service did not respond in time", ger.getMessage());
        }
    }

    @Nested
    @DisplayName("异常场景 - 响应体处理")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("空响应体映射 - 所有状态码应正确处理")
        void decode_nullBody_shouldUseReasonPhrase() {
            Map<String, Object> emptyMap = new HashMap<>();
            Response response = buildResponse(500, emptyMap);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ger.getHttpStatus());
            assertEquals("Internal Server Error", ger.getMessage());
        }

        @Test
        @DisplayName("无 error 字段的响应体 - 应使用 reason phrase")
        void decode_noErrorField_shouldUseReasonPhrase() throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("field", "username");
            errorBody.put("timestamp", System.currentTimeMillis());

            Response response = buildResponse(400, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(ValidationException.class, exception);
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 404, 409, 500, 502, 503, 504})
        @DisplayName("所有目标状态码 - 应映射为对应 HttpStatus")
        void decode_allStatusCodes_shouldMapCorrectly(int statusCode) throws IOException {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Test error for status: " + statusCode);

            Response response = buildResponse(statusCode, errorBody);

            Exception exception = errorDecoder.decode(TEST_METHOD_KEY, response);

            assertNotNull(exception);
            assertInstanceOf(GenericErrorResponse.class, exception);
            GenericErrorResponse ger = (GenericErrorResponse) exception;
            assertEquals(HttpStatus.valueOf(statusCode), ger.getHttpStatus());
        }
    }

    private Response buildResponse(int status, Map<String, Object> body) {
        String bodyString = null;
        if (body != null) {
            try {
                bodyString = objectMapper.writeValueAsString(body);
            } catch (IOException e) {
                bodyString = "{}";
            }
        }

        Response.Builder builder = Response.builder()
                .status(status)
                .reason(HttpStatus.valueOf(status).getReasonPhrase())
                .headers(java.util.Collections.emptyMap())
                .request(feign.Request.create(
                        feign.Request.HttpMethod.GET,
                        "http://test-url",
                        java.util.Collections.emptyMap(),
                        null,
                        null,
                        null
                ));

        if (bodyString != null) {
            builder.body(bodyString, StandardCharsets.UTF_8);
        }

        return builder.build();
    }
}
