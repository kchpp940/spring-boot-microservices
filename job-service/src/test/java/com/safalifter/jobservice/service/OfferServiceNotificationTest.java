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
import com.safalifter.jobservice.request.offer.MakeAnOfferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

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
    private OfferNotificationService offerNotificationService;

    @InjectMocks
    private OfferService offerService;

    @Test
    @DisplayName("makeAnOffer 应调用 offerNotificationService.notifyAdvertOwnerOfNewOffer")
    void testMakeAnOffer_NotifiesAdvertOwner() {
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

        verify(offerNotificationService, times(1)).notifyAdvertOwnerOfNewOffer(any(Offer.class));
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

        when(userServiceClient.getUserById(userId)).thenReturn(null);

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

        when(userServiceClient.getUserById(offerMakerUserId)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> offerService.makeAnOffer(request));
    }
}
