package com.safalifter.notificationservice.deserialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.safalifter.notificationservice.enums.NotificationType;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Nested
    @DisplayName("正常 JSON 反序列化")
    class NormalJsonTests {

        @Test
        @DisplayName("完整的 OFFER 通知 JSON 应正确反序列化")
        void deserialize_CompleteOfferNotification_Succeeds() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"advert-owner-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"You have received an offer for your advertising.\"," +
                    "\"notificationType\":\"OFFER\"," +
                    "\"traceId\":\"trace-001\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("advert-owner-123", request.getUserId());
            assertEquals("offer-456", request.getOfferId());
            assertEquals("You have received an offer for your advertising.", request.getMessage());
            assertEquals(NotificationType.OFFER, request.getNotificationType());
            assertEquals("trace-001", request.getTraceId());
        }

        @Test
        @DisplayName("完整的 SYSTEM_MESSAGE 通知 JSON 应正确反序列化")
        void deserialize_CompleteSystemMessage_Succeeds() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-789\"," +
                    "\"message\":\"System maintenance scheduled.\"," +
                    "\"notificationType\":\"SYSTEM_MESSAGE\"," +
                    "\"traceId\":\"trace-002\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("user-789", request.getUserId());
            assertNull(request.getOfferId());
            assertEquals("System maintenance scheduled.", request.getMessage());
            assertEquals(NotificationType.SYSTEM_MESSAGE, request.getNotificationType());
        }

        @Test
        @DisplayName("完整的 JOB_UPDATE 通知 JSON 应正确反序列化")
        void deserialize_CompleteJobUpdate_Succeeds() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-789\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Your job has been updated.\"," +
                    "\"notificationType\":\"JOB_UPDATE\"," +
                    "\"traceId\":\"trace-003\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals(NotificationType.JOB_UPDATE, request.getNotificationType());
        }
    }

    @Nested
    @DisplayName("缺字段 JSON 反序列化")
    class MissingFieldsJsonTests {

        @Test
        @DisplayName("缺少 notificationType 的 JSON 应反序列化为 null")
        void deserialize_MissingNotificationType_NullsOut() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-legacy\"," +
                    "\"offerId\":\"offer-legacy\"," +
                    "\"message\":\"Legacy format message\"," +
                    "\"traceId\":\"trace-legacy\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("user-legacy", request.getUserId());
            assertEquals("offer-legacy", request.getOfferId());
            assertEquals("Legacy format message", request.getMessage());
            assertNull(request.getNotificationType());
        }

        @Test
        @DisplayName("缺少 offerId 的 JSON（如系统通知）应反序列化成功")
        void deserialize_MissingOfferId_Succeeds() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"message\":\"System broadcast message\"," +
                    "\"notificationType\":\"SYSTEM_MESSAGE\"," +
                    "\"traceId\":\"trace-004\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("user-123", request.getUserId());
            assertNull(request.getOfferId());
            assertEquals("System broadcast message", request.getMessage());
            assertEquals(NotificationType.SYSTEM_MESSAGE, request.getNotificationType());
        }

        @Test
        @DisplayName("缺少 traceId 的 JSON 应反序列化为 null")
        void deserialize_MissingTraceId_NullsOut() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"OFFER\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(request.getTraceId());
            assertNotNull(request.getUserId());
            assertNotNull(request.getOfferId());
        }

        @Test
        @DisplayName("缺少 message 的 JSON 应反序列化为 null")
        void deserialize_MissingMessage_NullsOut() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"notificationType\":\"OFFER\"," +
                    "\"traceId\":\"trace-005\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(request.getMessage());
            assertNotNull(request.getUserId());
            assertEquals(NotificationType.OFFER, request.getNotificationType());
        }

        @Test
        @DisplayName("缺少 userId 的 JSON 应反序列化为 null")
        void deserialize_MissingUserId_NullsOut() throws JsonProcessingException {
            String json = "{" +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"OFFER\"," +
                    "\"traceId\":\"trace-006\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(request.getUserId());
            assertNotNull(request.getOfferId());
        }

        @Test
        @DisplayName("只有必要字段的 JSON 也能反序列化")
        void deserialize_MinimalJson_Succeeds() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-minimal\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("user-minimal", request.getUserId());
            assertNull(request.getOfferId());
            assertNull(request.getMessage());
            assertNull(request.getNotificationType());
            assertNull(request.getTraceId());
        }

        @Test
        @DisplayName("空 JSON 对象也能反序列化（所有字段为 null）")
        void deserialize_EmptyJsonObject_Succeeds() throws JsonProcessingException {
            String json = "{}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(request.getUserId());
            assertNull(request.getOfferId());
            assertNull(request.getMessage());
            assertNull(request.getNotificationType());
            assertNull(request.getTraceId());
        }
    }

    @Nested
    @DisplayName("未知 notificationType 反序列化")
    class UnknownNotificationTypeTests {

        @Test
        @DisplayName("未知 notificationType 字符串应抛出 InvalidFormatException")
        void deserialize_UnknownNotificationType_ThrowsInvalidFormatException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"UNKNOWN_TYPE\"," +
                    "\"traceId\":\"trace-007\"" +
                    "}";

            InvalidFormatException exception = assertThrows(InvalidFormatException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));

            assertTrue(exception.getMessage().contains("UNKNOWN_TYPE"));
            assertTrue(exception.getMessage().contains("NotificationType"));
        }

        @Test
        @DisplayName("notificationType 为 null 字符串应反序列化为 null")
        void deserialize_NullNotificationTypeString_NullsOut() throws JsonProcessingException {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":null," +
                    "\"traceId\":\"trace-008\"" +
                    "}";

            SendNotificationRequest request = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(request.getNotificationType());
            assertEquals("user-123", request.getUserId());
        }

        @Test
        @DisplayName("notificationType 为数组类型应抛出 JsonProcessingException（枚举无法从数组反序列化）")
        void deserialize_ArrayNotificationType_ThrowsException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":[\"OFFER\", \"EXTRA\"]," +
                    "\"traceId\":\"trace-009\"" +
                    "}";

            JsonProcessingException exception = assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));

            assertTrue(exception.getMessage().contains("notificationType"));
        }

        @Test
        @DisplayName("notificationType 为小写字符串应抛出异常（枚举区分大小写）")
        void deserialize_LowercaseNotificationType_ThrowsException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"offer\"," +
                    "\"traceId\":\"trace-010\"" +
                    "}";

            InvalidFormatException exception = assertThrows(InvalidFormatException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));

            assertTrue(exception.getMessage().contains("offer"));
        }

        @Test
        @DisplayName("notificationType 为空字符串应抛出异常")
        void deserialize_EmptyStringNotificationType_ThrowsException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"\"," +
                    "\"traceId\":\"trace-011\"" +
                    "}";

            InvalidFormatException exception = assertThrows(InvalidFormatException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));

            assertTrue(exception.getMessage().contains("NotificationType"));
        }
    }

    @Nested
    @DisplayName("异常 JSON 反序列化")
    class InvalidJsonTests {

        @Test
        @DisplayName("空字符串应抛出 MismatchedInputException")
        void deserialize_EmptyString_ThrowsException() {
            String json = "";

            assertThrows(MismatchedInputException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("null 字符串应反序列化为 null 对象")
        void deserialize_NullString_ReturnsNull() throws JsonProcessingException {
            String json = "null";

            SendNotificationRequest result = objectMapper.readValue(json, SendNotificationRequest.class);

            assertNull(result);
        }

        @Test
        @DisplayName("纯文本（非 JSON）应抛出 JsonProcessingException")
        void deserialize_PlainText_ThrowsException() {
            String json = "This is not a JSON string";

            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("截断的 JSON 应抛出 JsonProcessingException")
        void deserialize_TruncatedJson_ThrowsException() {
            String json = "{\"userId\":\"user-123\", \"offerId\":";

            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("多余逗号的 JSON 应抛出 JsonProcessingException")
        void deserialize_TrailingCommaJson_ThrowsException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "}";

            assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("数组而非对象应抛出 MismatchedInputException")
        void deserialize_JsonArray_ThrowsException() {
            String json = "[{\"userId\":\"user-123\"}]";

            assertThrows(MismatchedInputException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("值类型不匹配（userId 为数组）应抛出 JsonProcessingException")
        void deserialize_WrongTypeUserId_ThrowsException() {
            String json = "{" +
                    "\"userId\":[\"array\", \"not\", \"string\"]," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"OFFER\"" +
                    "}";

            JsonProcessingException exception = assertThrows(JsonProcessingException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));

            assertTrue(exception.getMessage().contains("userId"));
        }
    }

    @Nested
    @DisplayName("额外字段 JSON 反序列化")
    class ExtraFieldsJsonTests {

        @Test
        @DisplayName("默认配置下未知字段应抛出 UnrecognizedPropertyException")
        void deserialize_UnknownField_ThrowsUnrecognizedPropertyException() {
            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"OFFER\"," +
                    "\"unknownField\":\"should not exist\"" +
                    "}";

            assertThrows(UnrecognizedPropertyException.class,
                    () -> objectMapper.readValue(json, SendNotificationRequest.class));
        }

        @Test
        @DisplayName("忽略未知字段时，额外字段不影响反序列化")
        void deserialize_WithIgnoreUnknownFields_Succeeds() throws JsonProcessingException {
            ObjectMapper lenientMapper = new ObjectMapper();
            lenientMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            String json = "{" +
                    "\"userId\":\"user-123\"," +
                    "\"offerId\":\"offer-456\"," +
                    "\"message\":\"Test message\"," +
                    "\"notificationType\":\"OFFER\"," +
                    "\"extraField1\":\"value1\"," +
                    "\"extraField2\":123" +
                    "}";

            SendNotificationRequest request = lenientMapper.readValue(json, SendNotificationRequest.class);

            assertEquals("user-123", request.getUserId());
            assertEquals("offer-456", request.getOfferId());
            assertEquals("Test message", request.getMessage());
            assertEquals(NotificationType.OFFER, request.getNotificationType());
        }
    }
}
