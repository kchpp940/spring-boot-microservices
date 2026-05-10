package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.NotificationType;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.exc.IllegalStateTransitionException;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.repository.OfferRepository;
import com.safalifter.jobservice.request.notification.SendNotificationRequest;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferServiceStateTransitionTest {

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private AdvertService advertService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private KafkaTemplate<String, SendNotificationRequest> kafkaTemplate;

    @Mock
    private NewTopic topic;

    @InjectMocks
    private OfferService offerService;

    private static final String TOPIC_NAME = "notificationTopic";
    private static final String OFFER_ID = "offer-123";
    private static final String OFFER_MAKER_USER_ID = "user-offer-maker";
    private static final String ADVERT_OWNER_USER_ID = "user-advert-owner";
    private static final String ADVERT_ID = "advert-123";

    @BeforeEach
    void setUp() {
        when(topic.name()).thenReturn(TOPIC_NAME);
        NotificationPreferencesDto defaultPrefs = createDefaultPreferences();
        when(userServiceClient.getNotificationPreferences(anyString()))
                .thenReturn(ResponseEntity.ok(defaultPrefs));
    }

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
        ReflectionTestUtils.setField(offer, "version", 0L);
        return offer;
    }

    @Test
    @DisplayName("acceptOffer 应该将 OPEN 状态转换为 ACCEPTED 并发送通知")
    void testAcceptOffer_OpenToAccepted() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.acceptOffer(OFFER_ID);

        assertEquals(OfferStatus.ACCEPTED, result.getStatus());
        verify(offerRepository).save(any(Offer.class));

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has been accepted.", notification.getMessage());
    }

    @Test
    @DisplayName("rejectOffer 应该将 OPEN 状态转换为 REJECTED 并发送通知")
    void testRejectOffer_OpenToRejected() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.rejectOffer(OFFER_ID);

        assertEquals(OfferStatus.REJECTED, result.getStatus());
        verify(offerRepository).save(any(Offer.class));

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has been rejected.", notification.getMessage());
    }

    @Test
    @DisplayName("withdrawOffer 应该将 OPEN 状态转换为 WITHDRAWN 并通知广告所有者")
    void testWithdrawOffer_OpenToWithdrawn() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.withdrawOffer(OFFER_ID);

        assertEquals(OfferStatus.WITHDRAWN, result.getStatus());
        verify(offerRepository).save(any(Offer.class));

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(ADVERT_OWNER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("An offer has been withdrawn.", notification.getMessage());
    }

    @Test
    @DisplayName("expireOffer 应该将 OPEN 状态转换为 EXPIRED 并通知出价用户")
    void testExpireOffer_OpenToExpired() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.expireOffer(OFFER_ID);

        assertEquals(OfferStatus.EXPIRED, result.getStatus());
        verify(offerRepository).save(any(Offer.class));

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(eq(TOPIC_NAME), notificationCaptor.capture());

        SendNotificationRequest notification = notificationCaptor.getValue();
        assertEquals(OFFER_MAKER_USER_ID, notification.getUserId());
        assertEquals(OFFER_ID, notification.getOfferId());
        assertEquals("Your offer has expired.", notification.getMessage());
    }

    @Test
    @DisplayName("重复接受已接受的 offer 应该抛出 IllegalStateTransitionException")
    void testAcceptOffer_DuplicateOperation_AcceptedOffer() {
        Offer acceptedOffer = createOffer(OfferStatus.ACCEPTED);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(acceptedOffer));

        assertThrows(IllegalStateTransitionException.class, () -> offerService.acceptOffer(OFFER_ID));
        verify(offerRepository, never()).save(any(Offer.class));
        verify(kafkaTemplate, never()).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("对已拒绝的 offer 进行接受操作应该抛出 IllegalStateTransitionException")
    void testAcceptOffer_RejectedOffer_IllegalTransition() {
        Offer rejectedOffer = createOffer(OfferStatus.REJECTED);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(rejectedOffer));

        assertThrows(IllegalStateTransitionException.class, () -> offerService.acceptOffer(OFFER_ID));
        verify(offerRepository, never()).save(any(Offer.class));
    }

    @Test
    @DisplayName("对已撤回的 offer 进行拒绝操作应该抛出 IllegalStateTransitionException")
    void testRejectOffer_WithdrawnOffer_IllegalTransition() {
        Offer withdrawnOffer = createOffer(OfferStatus.WITHDRAWN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(withdrawnOffer));

        assertThrows(IllegalStateTransitionException.class, () -> offerService.rejectOffer(OFFER_ID));
        verify(offerRepository, never()).save(any(Offer.class));
    }

    @Test
    @DisplayName("对已过期的 offer 进行撤回操作应该抛出 IllegalStateTransitionException")
    void testWithdrawOffer_ExpiredOffer_IllegalTransition() {
        Offer expiredOffer = createOffer(OfferStatus.EXPIRED);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(expiredOffer));

        assertThrows(IllegalStateTransitionException.class, () -> offerService.withdrawOffer(OFFER_ID));
        verify(offerRepository, never()).save(any(Offer.class));
    }

    @Test
    @DisplayName("对不存在的 offer 操作应该抛出 NotFoundException")
    void testAcceptOffer_NotFound_ThrowsException() {
        when(offerRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> offerService.acceptOffer("non-existent-id"));
    }

    @Test
    @DisplayName("并发更新时，乐观锁应该抛出 ObjectOptimisticLockingFailureException")
    void testConcurrentUpdate_OptimisticLocking() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenThrow(new ObjectOptimisticLockingFailureException(Offer.class, OFFER_ID));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> offerService.acceptOffer(OFFER_ID));
    }

    @Test
    @DisplayName("当用户关闭通知时，acceptOffer 不应发送 Kafka 消息")
    void testAcceptOffer_NotificationDisabled_NoKafkaSend() {
        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(OFFER_MAKER_USER_ID);
        when(userServiceClient.getNotificationPreferences(OFFER_MAKER_USER_ID)).thenReturn(ResponseEntity.ok(prefs));

        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.acceptOffer(OFFER_ID);

        assertEquals(OfferStatus.ACCEPTED, result.getStatus());
        verify(kafkaTemplate, never()).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("当广告所有者关闭通知时，withdrawOffer 不应发送 Kafka 消息")
    void testWithdrawOffer_NotificationDisabled_NoKafkaSend() {
        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(ADVERT_OWNER_USER_ID);
        when(userServiceClient.getNotificationPreferences(ADVERT_OWNER_USER_ID)).thenReturn(ResponseEntity.ok(prefs));

        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offer result = offerService.withdrawOffer(OFFER_ID);

        assertEquals(OfferStatus.WITHDRAWN, result.getStatus());
        verify(kafkaTemplate, never()).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("isAdvertOwner 应该正确判断用户是否为广告所有者")
    void testIsAdvertOwner_ChecksAdvertOwner() {
        Offer openOffer = createOffer(OfferStatus.OPEN);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(openOffer));

        UserDto advertOwner = new UserDto();
        advertOwner.setId(ADVERT_OWNER_USER_ID);
        advertOwner.setUsername("advert-owner-username");
        when(userServiceClient.getUserById(ADVERT_OWNER_USER_ID)).thenReturn(ResponseEntity.ok(advertOwner));

        assertTrue(offerService.isAdvertOwner(OFFER_ID, "advert-owner-username"));
        assertFalse(offerService.isAdvertOwner(OFFER_ID, "other-user"));
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
}
