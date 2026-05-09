package com.safalifter.notificationservice.listeners;

import com.safalifter.notificationservice.enums.NotificationType;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import com.safalifter.notificationservice.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationListener notificationListener;

    @Test
    @DisplayName("listener 收到消息后应调用 NotificationService.save")
    void testConsume_CallsNotificationServiceSave() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .build();

        notificationListener.consume(request);

        verify(notificationService, times(1)).save(request);
    }

    @Test
    @DisplayName("listener 应正确传递所有字段到 save 方法")
    void testConsume_PassesAllFieldsToSave() {
        String expectedUserId = "test-user-789";
        String expectedOfferId = "offer-xyz";
        String expectedMessage = "You have received an offer for your advertising.";
        NotificationType expectedType = NotificationType.OFFER;

        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(expectedUserId)
                .offerId(expectedOfferId)
                .message(expectedMessage)
                .notificationType(expectedType)
                .build();

        notificationListener.consume(request);

        verify(notificationService).save(argThat(r ->
                expectedUserId.equals(r.getUserId()) &&
                expectedOfferId.equals(r.getOfferId()) &&
                expectedMessage.equals(r.getMessage()) &&
                expectedType.equals(r.getNotificationType())
        ));
    }

    @Test
    @DisplayName("listener 应正确处理 JOB_UPDATE 类型的通知")
    void testConsume_HandlesJobUpdateNotification() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .message("Job has been updated")
                .notificationType(NotificationType.JOB_UPDATE)
                .build();

        notificationListener.consume(request);

        verify(notificationService).save(argThat(r ->
                NotificationType.JOB_UPDATE.equals(r.getNotificationType())
        ));
    }

    @Test
    @DisplayName("listener 应正确处理 SYSTEM_MESSAGE 类型的通知")
    void testConsume_HandlesSystemMessageNotification() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .message("System maintenance scheduled")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .build();

        notificationListener.consume(request);

        verify(notificationService).save(argThat(r ->
                NotificationType.SYSTEM_MESSAGE.equals(r.getNotificationType())
        ));
    }

    @Test
    @DisplayName("旧消息缺少 notificationType 时，listener 不应抛异常")
    void testConsume_OldMessageWithNullType_DoesNotThrow() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-old")
                .offerId("offer-old")
                .message("Old format message")
                .notificationType(null)
                .build();

        notificationListener.consume(request);

        verify(notificationService, times(1)).save(argThat(r ->
                "user-old".equals(r.getUserId()) &&
                "offer-old".equals(r.getOfferId()) &&
                "Old format message".equals(r.getMessage()) &&
                r.getNotificationType() == null
        ));
    }
}
