package com.safalifter.notificationservice.listeners;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.safalifter.notificationservice.enums.NotificationType;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import com.safalifter.notificationservice.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationListener notificationListener;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger listenerLogger;

    @BeforeEach
    void setUp() {
        logAppender = new ListAppender<>();
        logAppender.start();

        listenerLogger = (Logger) LoggerFactory.getLogger(NotificationListener.class);
        listenerLogger.addAppender(logAppender);
        listenerLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            logAppender.stop();
        }
        if (listenerLogger != null) {
            listenerLogger.detachAppender(logAppender);
        }
    }

    private List<ILoggingEvent> getLogEvents() {
        return logAppender.list;
    }

    private boolean containsLogMessage(String expectedMessage) {
        return getLogEvents().stream()
                .anyMatch(event -> event.getFormattedMessage().contains(expectedMessage));
    }

    private boolean containsLogMessageWithLevel(String expectedMessage, Level level) {
        return getLogEvents().stream()
                .anyMatch(event -> event.getLevel() == level && event.getFormattedMessage().contains(expectedMessage));
    }

    @Test
    @DisplayName("正常消费 OFFER 通知：调用 save 并记录 info 日志，包含 type 字段")
    void consume_OfferNotification_CallsSaveAndLogsInfoWithType() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("advert-owner-123")
                .offerId("offer-456")
                .message("You have received an offer for your advertising.")
                .notificationType(NotificationType.OFFER)
                .traceId("trace-001")
                .build();

        notificationListener.consume(request);

        verify(notificationService, times(1)).save(request);
        assertTrue(containsLogMessageWithLevel("Consumed Kafka message", Level.INFO));
        assertTrue(containsLogMessage("userId=advert-owner-123"));
        assertTrue(containsLogMessage("offerId=offer-456"));
        assertTrue(containsLogMessage("type=OFFER"));
    }

    @Test
    @DisplayName("正常消费 SYSTEM_MESSAGE 通知：调用 save 并记录 info 日志，包含 type 字段")
    void consume_SystemMessageNotification_CallsSaveAndLogsInfoWithType() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-789")
                .message("System maintenance scheduled for tonight.")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .traceId("trace-002")
                .build();

        notificationListener.consume(request);

        verify(notificationService, times(1)).save(request);
        assertTrue(containsLogMessageWithLevel("Consumed Kafka message", Level.INFO));
        assertTrue(containsLogMessage("type=SYSTEM_MESSAGE"));
    }

    @Test
    @DisplayName("正常消费 JOB_UPDATE 通知：调用 save 并记录 info 日志，包含 type 字段")
    void consume_JobUpdateNotification_CallsSaveAndLogsInfoWithType() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-789")
                .offerId("offer-456")
                .message("Your job has been updated.")
                .notificationType(NotificationType.JOB_UPDATE)
                .traceId("trace-003")
                .build();

        notificationListener.consume(request);

        verify(notificationService, times(1)).save(request);
        assertTrue(containsLogMessageWithLevel("Consumed Kafka message", Level.INFO));
        assertTrue(containsLogMessage("type=JOB_UPDATE"));
    }

    @Test
    @DisplayName("缺字段：notificationType 为 null 时不应抛异常，日志显示 type=null")
    void consume_NullNotificationType_DoesNotThrow_LogsTypeAsNull() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-old-format")
                .offerId("offer-legacy")
                .message("Legacy format message")
                .notificationType(null)
                .build();

        assertDoesNotThrow(() -> notificationListener.consume(request));

        verify(notificationService, times(1)).save(request);
        assertTrue(containsLogMessageWithLevel("Consumed Kafka message", Level.INFO));
        assertTrue(containsLogMessage("type=null"));
    }

    @Test
    @DisplayName("缺字段：offerId 为 null（如 SYSTEM_MESSAGE）时不应抛异常")
    void consume_NullOfferId_DoesNotThrow() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .message("System broadcast message")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .offerId(null)
                .build();

        assertDoesNotThrow(() -> notificationListener.consume(request));

        verify(notificationService, times(1)).save(request);
    }

    @Test
    @DisplayName("缺字段：traceId 为 null 时应记录 debug 日志")
    void consume_NullTraceId_LogsDebug() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .traceId(null)
                .build();

        assertDoesNotThrow(() -> notificationListener.consume(request));

        verify(notificationService, times(1)).save(request);
        assertTrue(containsLogMessageWithLevel("Kafka message has no traceId", Level.DEBUG));
    }

    @Test
    @DisplayName("缺字段：message 为 null 时不应抛异常")
    void consume_NullMessage_DoesNotThrow() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .notificationType(NotificationType.OFFER)
                .message(null)
                .build();

        assertDoesNotThrow(() -> notificationListener.consume(request));

        verify(notificationService, times(1)).save(request);
    }

    @Test
    @DisplayName("业务异常传播：数据库保存失败时应向上抛出异常")
    void consume_DatabaseSaveFailure_PropagatesException() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        RuntimeException expectedException = new RuntimeException("Database connection failed");
        doThrow(expectedException).when(notificationService).save(request);

        RuntimeException actualException = assertThrows(RuntimeException.class,
                () -> notificationListener.consume(request));

        assertSame(expectedException, actualException);
        verify(notificationService, times(1)).save(request);
    }

    @Test
    @DisplayName("业务异常传播：任何异常都应向上抛出，由 Kafka 容器处理")
    void consume_AnyExceptionFromSave_PropagatesToKafkaContainer() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        IllegalStateException expectedException = new IllegalStateException("Unexpected business state");
        doThrow(expectedException).when(notificationService).save(request);

        IllegalStateException actualException = assertThrows(IllegalStateException.class,
                () -> notificationListener.consume(request));

        assertSame(expectedException, actualException);
    }

    @Test
    @DisplayName("业务异常传播：异常消息应保持原样")
    void consume_ExceptionMessage_Preserved() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        String expectedMessage = "Constraint violation: duplicate key";
        doThrow(new RuntimeException(expectedMessage)).when(notificationService).save(request);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> notificationListener.consume(request));

        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName("空指针异常：request 为 null 时应抛出 NullPointerException")
    void consume_NullRequest_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> notificationListener.consume(null));
    }

    @Test
    @DisplayName("异常前日志：抛出异常前仍应记录消费日志")
    void consume_Exception_StillLogsConsumption() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        doThrow(new RuntimeException("DB error")).when(notificationService).save(request);

        assertThrows(RuntimeException.class,
                () -> notificationListener.consume(request));

        assertTrue(containsLogMessageWithLevel("Consumed Kafka message", Level.INFO));
        assertTrue(containsLogMessage("userId=user-123"));
        assertTrue(containsLogMessage("offerId=offer-456"));
        assertTrue(containsLogMessage("type=OFFER"));
    }

    @Test
    @DisplayName("日志输出：成功消费应输出 info 级别日志")
    void consume_Success_LogsInfo() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        notificationListener.consume(request);

        List<ILoggingEvent> events = getLogEvents();
        assertTrue(events.stream()
                .anyMatch(event -> event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Consumed Kafka message")));
    }

    @Test
    @DisplayName("日志输出：traceId 不为 null 时不记录 debug 日志")
    void consume_WithTraceId_DoesNotLogDebug() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .traceId("trace-valid-123")
                .build();

        notificationListener.consume(request);

        assertFalse(containsLogMessage("Kafka message has no traceId"));
    }
}
