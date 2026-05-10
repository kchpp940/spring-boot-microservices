package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.enums.NotificationType;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.request.notification.SendNotificationRequest;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferNotificationServiceTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private KafkaTemplate<String, SendNotificationRequest> kafkaTemplate;

    @Mock
    private NewTopic topic;

    @InjectMocks
    private OfferNotificationService offerNotificationService;

    private static final String TOPIC_NAME = "notificationTopic";
    private static final String OFFER_ID = "offer-123";
    private static final String OFFER_MAKER_USER_ID = "user-offer-maker";
    private static final String ADVERT_OWNER_USER_ID = "user-advert-owner";
    private static final String ADVERT_ID = "advert-123";

    private NotificationPreferencesDto createDefaultPreferences() {
        Map<NotificationType, Boolean> prefs = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            prefs.put(type, true);
        }
        return NotificationPreferencesDto.builder()
                .userId("test-user")
                .preferences(prefs)
                .build();
    }

    private NotificationPreferencesDto createPreferencesWithOfferDisabled(String userId) {
        Map<NotificationType, Boolean> prefs = new EnumMap<>(NotificationType.class);
        prefs.put(NotificationType.OFFER, false);
        prefs.put(NotificationType.JOB_UPDATE, true);
        prefs.put(NotificationType.SYSTEM_MESSAGE, true);
        return NotificationPreferencesDto.builder()
                .userId(userId)
                .preferences(prefs)
                .build();
    }

    private Advert createAdvert() {
        Advert advert = Advert.builder()
                .userId(ADVERT_OWNER_USER_ID)
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", ADVERT_ID);
        return advert;
    }

    private Offer createOffer(OfferStatus status) {
        Advert advert = createAdvert();
        Offer offer = Offer.builder()
                .userId(OFFER_MAKER_USER_ID)
                .offeredPrice(500)
                .status(status)
                .advert(advert)
                .build();
        ReflectionTestUtils.setField(offer, "id", OFFER_ID);
        return offer;
    }

    @Test
    @DisplayName("notifyAdvertOwnerOfNewOffer 应向广告所有者发送新offer通知")
    void testNotifyAdvertOwnerOfNewOffer_SendsCorrectNotification() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.OPEN);
        when(userServiceClient.getNotificationPreferences(ADVERT_OWNER_USER_ID))
                .thenReturn(ResponseEntity.ok(createDefaultPreferences()));

        offerNotificationService.notifyAdvertOwnerOfNewOffer(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(ADVERT_OWNER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("You have received an offer for your advertising.", notification.getMessage());
        assertEquals(NotificationType.OFFER, notification.getNotificationType());
    }

    @Test
    @DisplayName("notifyOfferMakerOfAcceptance 应向offer发起者发送接受通知")
    void testNotifyOfferMakerOfAcceptance_SendsCorrectNotification() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.ACCEPTED);
        when(userServiceClient.getNotificationPreferences(OFFER_MAKER_USER_ID))
                .thenReturn(ResponseEntity.ok(createDefaultPreferences()));

        offerNotificationService.notifyOfferMakerOfAcceptance(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has been accepted.", notification.getMessage());
        assertEquals(NotificationType.OFFER, notification.getNotificationType());
    }

    @Test
    @DisplayName("notifyOfferMakerOfRejection 应向offer发起者发送拒绝通知")
    void testNotifyOfferMakerOfRejection_SendsCorrectNotification() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.REJECTED);
        when(userServiceClient.getNotificationPreferences(OFFER_MAKER_USER_ID))
                .thenReturn(ResponseEntity.ok(createDefaultPreferences()));

        offerNotificationService.notifyOfferMakerOfRejection(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has been rejected.", notification.getMessage());
        assertEquals(NotificationType.OFFER, notification.getNotificationType());
    }

    @Test
    @DisplayName("notifyAdvertOwnerOfWithdrawal 应向广告所有者发送撤回通知")
    void testNotifyAdvertOwnerOfWithdrawal_SendsCorrectNotification() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.WITHDRAWN);
        when(userServiceClient.getNotificationPreferences(ADVERT_OWNER_USER_ID))
                .thenReturn(ResponseEntity.ok(createDefaultPreferences()));

        offerNotificationService.notifyAdvertOwnerOfWithdrawal(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(ADVERT_OWNER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("An offer has been withdrawn.", notification.getMessage());
        assertEquals(NotificationType.OFFER, notification.getNotificationType());
    }

    @Test
    @DisplayName("notifyOfferMakerOfExpiration 应向offer发起者发送过期通知")
    void testNotifyOfferMakerOfExpiration_SendsCorrectNotification() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.EXPIRED);
        when(userServiceClient.getNotificationPreferences(OFFER_MAKER_USER_ID))
                .thenReturn(ResponseEntity.ok(createDefaultPreferences()));

        offerNotificationService.notifyOfferMakerOfExpiration(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has expired.", notification.getMessage());
        assertEquals(NotificationType.OFFER, notification.getNotificationType());
    }

    @Test
    @DisplayName("getNotificationPreferences 应正确解析用户偏好")
    void testGetNotificationPreferences_ParsesPreferencesCorrectly() {
        String userId = "user-123";
        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(userId);

        when(userServiceClient.getNotificationPreferences(userId)).thenReturn(ResponseEntity.ok(prefs));

        NotificationPreferencesDto result = offerNotificationService.getNotificationPreferences(userId);

        assertEquals(userId, result.getUserId());
        assertFalse(result.isEnabled(NotificationType.OFFER));
        assertTrue(result.isEnabled(NotificationType.JOB_UPDATE));
        assertTrue(result.isEnabled(NotificationType.SYSTEM_MESSAGE));
    }

    @Test
    @DisplayName("当用户关闭通知时，不应发送 Kafka 消息")
    void testWhenNotificationDisabled_NoKafkaSend() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.OPEN);
        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(ADVERT_OWNER_USER_ID);
        when(userServiceClient.getNotificationPreferences(ADVERT_OWNER_USER_ID)).thenReturn(ResponseEntity.ok(prefs));

        offerNotificationService.notifyAdvertOwnerOfNewOffer(offer);

        verify(kafkaTemplate, never()).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("Feign 返回 null 时，应使用默认偏好")
    void testWhenFeignReturnsNull_UsesDefaultPreferences() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        Offer offer = createOffer(OfferStatus.OPEN);
        when(userServiceClient.getNotificationPreferences(ADVERT_OWNER_USER_ID)).thenReturn(null);

        offerNotificationService.notifyAdvertOwnerOfNewOffer(offer);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(ADVERT_OWNER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
    }
}
