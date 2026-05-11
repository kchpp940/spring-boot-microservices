package com.safalifter.notificationservice.service;

import com.safalifter.notificationservice.enums.NotificationType;
import com.safalifter.notificationservice.model.Notification;
import com.safalifter.notificationservice.repository.NotificationRepository;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("正常持久化：OFFER 通知应保存到数据库")
    void save_OfferNotification_PersistsToDatabase() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("advert-owner-123")
                .offerId("offer-456")
                .message("You have received an offer for your advertising.")
                .notificationType(NotificationType.OFFER)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("advert-owner-123", saved.getUserId());
        assertEquals("offer-456", saved.getOfferId());
        assertEquals("You have received an offer for your advertising.", saved.getMessage());
        assertEquals(NotificationType.OFFER, saved.getType());
        assertNotNull(saved.getId());
    }

    @Test
    @DisplayName("正常持久化：SYSTEM_MESSAGE 通知应保存到数据库（offerId 为 null）")
    void save_SystemMessageNotification_PersistsToDatabase() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-789")
                .message("System maintenance scheduled.")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .offerId(null)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("user-789", saved.getUserId());
        assertNull(saved.getOfferId());
        assertEquals("System maintenance scheduled.", saved.getMessage());
        assertEquals(NotificationType.SYSTEM_MESSAGE, saved.getType());
    }

    @Test
    @DisplayName("正常持久化：JOB_UPDATE 通知应保存到数据库")
    void save_JobUpdateNotification_PersistsToDatabase() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-789")
                .offerId("offer-456")
                .message("Your job has been updated.")
                .notificationType(NotificationType.JOB_UPDATE)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.JOB_UPDATE, saved.getType());
    }

    @Test
    @DisplayName("缺字段：notificationType 为 null 时仍应保存")
    void save_NullNotificationType_StillSaves() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-legacy")
                .offerId("offer-legacy")
                .message("Legacy format message")
                .notificationType(null)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertNull(saved.getType());
    }

    @Test
    @DisplayName("缺字段：offerId 为 null 时仍应保存（如系统通知）")
    void save_NullOfferId_StillSaves() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .message("System broadcast")
                .notificationType(NotificationType.SYSTEM_MESSAGE)
                .offerId(null)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertNull(saved.getOfferId());
    }

    @Test
    @DisplayName("缺字段：message 为 null 时仍应保存")
    void save_NullMessage_StillSaves() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .notificationType(NotificationType.OFFER)
                .message(null)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertNull(saved.getMessage());
    }

    @Test
    @DisplayName("重复 offerId：相同 offerId 但不同消息应分别保存")
    void save_SameOfferIdDifferentMessage_SavesBoth() {
        SendNotificationRequest request1 = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Offer received")
                .notificationType(NotificationType.OFFER)
                .build();

        SendNotificationRequest request2 = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Offer accepted")
                .notificationType(NotificationType.OFFER)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request1);
        notificationService.save(request2);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertEquals(2, captor.getAllValues().size());
        assertEquals("Offer received", captor.getAllValues().get(0).getMessage());
        assertEquals("Offer accepted", captor.getAllValues().get(1).getMessage());
        assertNotEquals(captor.getAllValues().get(0).getId(), captor.getAllValues().get(1).getId());
    }

    @Test
    @DisplayName("重复 offerId：相同 offerId 但不同类型应分别保存")
    void save_SameOfferIdDifferentType_SavesBoth() {
        SendNotificationRequest offerRequest = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        SendNotificationRequest jobUpdateRequest = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.JOB_UPDATE)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(offerRequest);
        notificationService.save(jobUpdateRequest);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertEquals(NotificationType.OFFER, captor.getAllValues().get(0).getType());
        assertEquals(NotificationType.JOB_UPDATE, captor.getAllValues().get(1).getType());
        assertNotEquals(captor.getAllValues().get(0).getId(), captor.getAllValues().get(1).getId());
    }

    @Test
    @DisplayName("重复 offerId：完全相同的消息也会被保存多次（无去重机制）")
    void save_IdenticalMessage_SavesMultipleTimes_NoDeduplication() {
        SendNotificationRequest duplicateRequest = SendNotificationRequest.builder()
                .userId("advert-owner-123")
                .offerId("offer-456")
                .message("You have received an offer for your advertising.")
                .notificationType(NotificationType.OFFER)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(duplicateRequest);
        notificationService.save(duplicateRequest);
        notificationService.save(duplicateRequest);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(captor.capture());

        assertEquals(3, captor.getAllValues().size());

        for (Notification n : captor.getAllValues()) {
            assertEquals("advert-owner-123", n.getUserId());
            assertEquals("offer-456", n.getOfferId());
            assertEquals("You have received an offer for your advertising.", n.getMessage());
            assertEquals(NotificationType.OFFER, n.getType());
        }

        String id1 = captor.getAllValues().get(0).getId();
        String id2 = captor.getAllValues().get(1).getId();
        String id3 = captor.getAllValues().get(2).getId();

        assertNotEquals(id1, id2, "Each saved notification should have unique ID (id1 vs id2)");
        assertNotEquals(id1, id3, "Each saved notification should have unique ID (id1 vs id3)");
        assertNotEquals(id2, id3, "Each saved notification should have unique ID (id2 vs id3)");
    }

    @Test
    @DisplayName("重复消费策略：相同 offerId 不同用户的通知都应保存")
    void save_SameOfferIdDifferentUsers_BothSaved() {
        SendNotificationRequest advertOwnerRequest = SendNotificationRequest.builder()
                .userId("advert-owner")
                .offerId("offer-456")
                .message("You have received an offer for your advertising.")
                .notificationType(NotificationType.OFFER)
                .build();

        SendNotificationRequest offerMakerRequest = SendNotificationRequest.builder()
                .userId("offer-maker")
                .offerId("offer-456")
                .message("Your offer has been sent to advert owner.")
                .notificationType(NotificationType.OFFER)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(advertOwnerRequest);
        notificationService.save(offerMakerRequest);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertEquals(2, captor.getAllValues().size());
        assertEquals("advert-owner", captor.getAllValues().get(0).getUserId());
        assertEquals("offer-maker", captor.getAllValues().get(1).getUserId());
        assertEquals("offer-456", captor.getAllValues().get(0).getOfferId());
        assertEquals("offer-456", captor.getAllValues().get(1).getOfferId());
        assertNotEquals(captor.getAllValues().get(0).getId(), captor.getAllValues().get(1).getId());
    }

    @Test
    @DisplayName("数据库保存失败：应抛出异常，由上层处理")
    void save_DatabaseFailure_ThrowsException() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        doThrow(new RuntimeException("Database connection failed"))
                .when(notificationRepository).save(any(Notification.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> notificationService.save(request));

        assertEquals("Database connection failed", exception.getMessage());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("数据库保存失败：任何异常都应向上抛出")
    void save_AnyDatabaseException_IsPropagated() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId("user-123")
                .offerId("offer-456")
                .message("Test message")
                .notificationType(NotificationType.OFFER)
                .build();

        doThrow(new IllegalStateException("Constraint violation"))
                .when(notificationRepository).save(any(Notification.class));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> notificationService.save(request));

        assertEquals("Constraint violation", exception.getMessage());
    }

    @Test
    @DisplayName("生成 UUID：每个通知都应有唯一 ID")
    void save_GeneratesUniqueIdForEachNotification() {
        SendNotificationRequest request1 = SendNotificationRequest.builder()
                .userId("user-1")
                .offerId("offer-1")
                .message("Msg 1")
                .notificationType(NotificationType.OFFER)
                .build();

        SendNotificationRequest request2 = SendNotificationRequest.builder()
                .userId("user-2")
                .offerId("offer-2")
                .message("Msg 2")
                .notificationType(NotificationType.OFFER)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.save(request1);
        notificationService.save(request2);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        String id1 = captor.getAllValues().get(0).getId();
        String id2 = captor.getAllValues().get(1).getId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("getAllByUserId 应委托给 repository")
    void getAllByUserId_DelegatesToRepository() {
        String userId = "user-123";

        notificationService.getAllByUserId(userId);

        verify(notificationRepository, times(1)).findAllByUserIdOrderByCreationTimestampDesc(userId);
    }

    @Test
    @DisplayName("getAllByOfferId 应委托给 repository")
    void getAllByOfferId_DelegatesToRepository() {
        String offerId = "offer-456";

        notificationService.getAllByOfferId(offerId);

        verify(notificationRepository, times(1)).findAllByOfferIdOrderByCreationTimestampDesc(offerId);
    }
}
