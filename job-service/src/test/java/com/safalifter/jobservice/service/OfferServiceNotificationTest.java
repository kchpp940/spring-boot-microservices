package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.NotificationType;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.repository.OfferRepository;
import com.safalifter.jobservice.request.notification.SendNotificationRequest;
import com.safalifter.jobservice.request.offer.MakeAnOfferRequest;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferServiceNotificationTest {

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

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private OfferService offerService;

    private static final String EXPECTED_TOPIC = "notificationTopic";
    private static final String EXPECTED_MESSAGE = "You have received an offer for your advertising.";

    @BeforeEach
    void setUp() {
        when(topic.name()).thenReturn(EXPECTED_TOPIC);
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

    @Test
    @DisplayName("makeAnOffer 应向 notificationTopic 发送 SendNotificationRequest")
    void testMakeAnOffer_SendsNotificationToCorrectTopic() {
        String offerMakerUserId = "user-offer-maker";
        String advertOwnerUserId = "user-advert-owner";
        String advertId = "advert-123";
        int offeredPrice = 500;

        UserDto userDto = new UserDto();
        userDto.setId(offerMakerUserId);

        Advert advert = Advert.builder()
                .userId(advertOwnerUserId)
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", advertId);

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(offeredPrice);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(ResponseEntity.ok(userDto));
        when(advertService.getAdvertById(advertId)).thenReturn(advert);
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "offer-456");
            return saved;
        });

        Offer result = offerService.makeAnOffer(request);

        assertNotNull(result);
        assertEquals(OfferStatus.OPEN, result.getStatus());
        assertEquals(offeredPrice, result.getOfferedPrice());
        assertEquals(offerMakerUserId, result.getUserId());
        assertEquals(advert, result.getAdvert());

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), notificationCaptor.capture());

        assertEquals(EXPECTED_TOPIC, topicCaptor.getValue());

        SendNotificationRequest capturedNotification = notificationCaptor.getValue();
        assertEquals(advertOwnerUserId, capturedNotification.getUserId());
        assertEquals("offer-456", capturedNotification.getOfferId());
        assertEquals(EXPECTED_MESSAGE, capturedNotification.getMessage());
    }

    @Test
    @DisplayName("makeAnOffer 发送的 notification 应包含 advert owner 的 userId")
    void testMakeAnOffer_NotificationUsesAdvertOwnerUserId() {
        String offerMakerUserId = "user-buyer";
        String advertOwnerUserId = "user-seller-999";
        String advertId = "advert-abc";

        UserDto userDto = new UserDto();
        userDto.setId(offerMakerUserId);

        Advert advert = Advert.builder()
                .userId(advertOwnerUserId)
                .build();
        ReflectionTestUtils.setField(advert, "id", advertId);

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(1000);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(ResponseEntity.ok(userDto));
        when(advertService.getAdvertById(advertId)).thenReturn(advert);
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "offer-xyz");
            return saved;
        });

        offerService.makeAnOffer(request);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(anyString(), notificationCaptor.capture());

        assertEquals(advertOwnerUserId, notificationCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("NewTopic bean 应返回 notificationTopic")
    void testTopicBeanName() {
        assertEquals(EXPECTED_TOPIC, topic.name());
    }

    @Test
    @DisplayName("makeAnOffer 发送的 notification 应包含正确的 notificationType=OFFER")
    void testMakeAnOffer_NotificationContainsCorrectType() {
        String offerMakerUserId = "user-offer-maker";
        String advertOwnerUserId = "user-advert-owner";
        String advertId = "advert-123";

        UserDto userDto = new UserDto();
        userDto.setId(offerMakerUserId);

        Advert advert = Advert.builder()
                .userId(advertOwnerUserId)
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", advertId);

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(500);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(ResponseEntity.ok(userDto));
        when(advertService.getAdvertById(advertId)).thenReturn(advert);
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "offer-456");
            return saved;
        });

        offerService.makeAnOffer(request);

        ArgumentCaptor<SendNotificationRequest> notificationCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(kafkaTemplate).send(anyString(), notificationCaptor.capture());

        assertEquals(NotificationType.OFFER, notificationCaptor.getValue().getNotificationType());
    }

    @Test
    @DisplayName("当用户关闭 OFFER 通知时，makeAnOffer 不应发送 Kafka 消息")
    void testMakeAnOffer_SkipNotificationWhenOfferDisabled() {
        String offerMakerUserId = "user-offer-maker";
        String advertOwnerUserId = "user-advert-owner";
        String advertId = "advert-123";

        UserDto userDto = new UserDto();
        userDto.setId(offerMakerUserId);

        Advert advert = Advert.builder()
                .userId(advertOwnerUserId)
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", advertId);

        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(advertOwnerUserId);

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(500);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(ResponseEntity.ok(userDto));
        when(advertService.getAdvertById(advertId)).thenReturn(advert);
        when(userServiceClient.getNotificationPreferences(advertOwnerUserId)).thenReturn(ResponseEntity.ok(prefs));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "offer-456");
            return saved;
        });

        Offer result = offerService.makeAnOffer(request);

        assertNotNull(result);
        verify(kafkaTemplate, never()).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("Feign 调用失败返回 null 时，应使用默认偏好并发送通知")
    void testMakeAnOffer_UsesDefaultPreferencesWhenFeignFails() {
        String offerMakerUserId = "user-offer-maker";
        String advertOwnerUserId = "user-advert-owner";
        String advertId = "advert-123";

        UserDto userDto = new UserDto();
        userDto.setId(offerMakerUserId);

        Advert advert = Advert.builder()
                .userId(advertOwnerUserId)
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", advertId);

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(500);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(ResponseEntity.ok(userDto));
        when(advertService.getAdvertById(advertId)).thenReturn(advert);
        when(userServiceClient.getNotificationPreferences(advertOwnerUserId)).thenReturn(null);
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "offer-456");
            return saved;
        });

        Offer result = offerService.makeAnOffer(request);

        assertNotNull(result);
        verify(kafkaTemplate, times(1)).send(anyString(), any(SendNotificationRequest.class));
    }

    @Test
    @DisplayName("getNotificationPreferences 应正确解析用户偏好")
    void testGetNotificationPreferences_ParsesPreferencesCorrectly() {
        String userId = "user-123";
        NotificationPreferencesDto prefs = createPreferencesWithOfferDisabled(userId);

        when(userServiceClient.getNotificationPreferences(userId)).thenReturn(ResponseEntity.ok(prefs));

        NotificationPreferencesDto result = offerService.getNotificationPreferences(userId);

        assertEquals(userId, result.getUserId());
        assertFalse(result.isEnabled(NotificationType.OFFER));
        assertTrue(result.isEnabled(NotificationType.JOB_UPDATE));
        assertTrue(result.isEnabled(NotificationType.SYSTEM_MESSAGE));
    }

    @Test
    @DisplayName("createDefault 应创建全部开启的默认偏好")
    void testCreateDefaultPreferences_AllEnabled() {
        String userId = "user-default";
        NotificationPreferencesDto defaultPrefs = NotificationPreferencesDto.createDefault(userId);

        assertEquals(userId, defaultPrefs.getUserId());
        for (NotificationType type : NotificationType.values()) {
            assertTrue(defaultPrefs.isEnabled(type), "Expected " + type + " to be enabled by default");
        }
    }

    @Test
    @DisplayName("getUserById 当 Feign 返回 null 时应抛出 NotFoundException，不应 NPE")
    void testGetUserById_FeignReturnsNull_ThrowsNotFoundException() {
        String userId = "non-existent-user";

        when(userServiceClient.getUserById(userId)).thenReturn(null);  // ← mock Feign fallback 返回 null

        assertThrows(NotFoundException.class, () -> offerService.getUserById(userId));
    }

    @Test
    @DisplayName("getUserById 当 Feign 返回 ResponseEntity.ok(null) 时应抛出 NotFoundException")
    void testGetUserById_FeignReturnsNullBody_ThrowsNotFoundException() {
        String userId = "non-existent-user";

        when(userServiceClient.getUserById(userId)).thenReturn(ResponseEntity.ok(null));

        assertThrows(NotFoundException.class, () -> offerService.getUserById(userId));
    }

    @Test
    @DisplayName("makeAnOffer 当 getUserById Feign 返回 null 时应抛出 NotFoundException，不应 NPE")
    void testMakeAnOffer_GetUserByIdFeignNull_ThrowsNotFoundException() {
        String offerMakerUserId = "user-offer-maker";
        String advertId = "advert-123";

        MakeAnOfferRequest request = new MakeAnOfferRequest();
        request.setUserId(offerMakerUserId);
        request.setAdvertId(advertId);
        request.setOfferedPrice(500);

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(null);  // ← mock Feign fallback 返回 null

        assertThrows(NotFoundException.class, () -> offerService.makeAnOffer(request));
    }
}
