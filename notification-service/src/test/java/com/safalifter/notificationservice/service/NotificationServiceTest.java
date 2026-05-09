package com.safalifter.notificationservice.service;

import com.safalifter.notificationservice.model.Notification;
import com.safalifter.notificationservice.repository.NotificationRepository;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private SendNotificationRequest testRequest;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testRequest = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test notification message")
                .build();

        testNotification = Notification.builder()
                .id("notif-uuid")
                .userId("user-123")
                .offerId("offer-456")
                .message("Test notification message")
                .creationTimestamp(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("save 应将 SendNotificationRequest 保存为 Notification")
    void testSave_PersistsNotification() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        notificationService.save(testRequest);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());

        Notification saved = notificationCaptor.getValue();
        assertEquals(testRequest.getUserId(), saved.getUserId());
        assertEquals(testRequest.getOfferId(), saved.getOfferId());
        assertEquals(testRequest.getMessage(), saved.getMessage());
        assertNotNull(saved.getId());
    }

    @Test
    @DisplayName("save 应为每个通知生成 UUID")
    void testSave_GeneratesUUID() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            assertNotNull(n.getId(), "ID should be set before save");
            return n;
        });

        notificationService.save(testRequest);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("getAllByUserId 应按创建时间降序返回通知")
    void testGetAllByUserId_ReturnsNotificationsOrderByCreationTimestampDesc() {
        String userId = "user-123";
        Notification older = Notification.builder()
                .id("notif-1")
                .userId(userId)
                .offerId("offer-1")
                .message("Older notification")
                .creationTimestamp(LocalDateTime.now().minusDays(2))
                .build();
        Notification newer = Notification.builder()
                .id("notif-2")
                .userId(userId)
                .offerId("offer-2")
                .message("Newer notification")
                .creationTimestamp(LocalDateTime.now().minusDays(1))
                .build();

        when(notificationRepository.findAllByUserIdOrderByCreationTimestampDesc(userId))
                .thenReturn(Arrays.asList(newer, older));

        List<Notification> result = notificationService.getAllByUserId(userId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("notif-2", result.get(0).getId());
        assertEquals("notif-1", result.get(1).getId());

        verify(notificationRepository).findAllByUserIdOrderByCreationTimestampDesc(userId);
    }

    @Test
    @DisplayName("getAllByUserId 应查询正确的 userId")
    void testGetAllByUserId_UsesCorrectUserId() {
        String specificUserId = "specific-user-999";

        notificationService.getAllByUserId(specificUserId);

        verify(notificationRepository).findAllByUserIdOrderByCreationTimestampDesc(specificUserId);
        verify(notificationRepository, never()).findAllByUserIdOrderByCreationTimestampDesc("different-user");
    }

    @Test
    @DisplayName("getAllByUserId 对于无通知的用户应返回空列表")
    void testGetAllByUserId_ReturnsEmptyListWhenNoNotifications() {
        String userIdWithNoNotifications = "user-with-no-notifications";

        when(notificationRepository.findAllByUserIdOrderByCreationTimestampDesc(userIdWithNoNotifications))
                .thenReturn(List.of());

        List<Notification> result = notificationService.getAllByUserId(userIdWithNoNotifications);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
