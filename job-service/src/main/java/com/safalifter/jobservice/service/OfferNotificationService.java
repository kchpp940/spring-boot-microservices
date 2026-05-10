package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.enums.NotificationType;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.request.notification.SendNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferNotificationService {

    private final UserServiceClient userServiceClient;
    private final KafkaTemplate<String, SendNotificationRequest> kafkaTemplate;
    private final NewTopic topic;

    public NotificationPreferencesDto getNotificationPreferences(String userId) {
        ResponseEntity<NotificationPreferencesDto> response = userServiceClient.getNotificationPreferences(userId);
        if (response != null && response.getBody() != null) {
            return response.getBody();
        }
        log.warn("Failed to get notification preferences for userId: {}, using default", userId);
        return NotificationPreferencesDto.createDefault(userId);
    }

    public void notifyAdvertOwnerOfNewOffer(Offer offer) {
        Advert advert = offer.getAdvert();
        String advertOwnerUserId = advert.getUserId();
        NotificationPreferencesDto prefs = getNotificationPreferences(advertOwnerUserId);

        if (prefs.isEnabled(NotificationType.OFFER)) {
            SendNotificationRequest notification = SendNotificationRequest.builder()
                    .message("You have received an offer for your advertising.")
                    .userId(advertOwnerUserId)
                    .offerId(offer.getId())
                    .notificationType(NotificationType.OFFER)
                    .build();

            kafkaTemplate.send(topic.name(), notification);
        } else {
            log.info("User {} has disabled OFFER notifications, skipping send", advertOwnerUserId);
        }
    }

    public void notifyOfferMakerOfAcceptance(Offer offer) {
        notifyOfferMaker(offer, "Your offer has been accepted.");
    }

    public void notifyOfferMakerOfRejection(Offer offer) {
        notifyOfferMaker(offer, "Your offer has been rejected.");
    }

    public void notifyAdvertOwnerOfWithdrawal(Offer offer) {
        String advertOwnerUserId = offer.getAdvert().getUserId();
        NotificationPreferencesDto prefs = getNotificationPreferences(advertOwnerUserId);

        if (prefs.isEnabled(NotificationType.OFFER)) {
            SendNotificationRequest notification = SendNotificationRequest.builder()
                    .message("An offer has been withdrawn.")
                    .userId(advertOwnerUserId)
                    .offerId(offer.getId())
                    .notificationType(NotificationType.OFFER)
                    .build();

            kafkaTemplate.send(topic.name(), notification);
        }
    }

    public void notifyOfferMakerOfExpiration(Offer offer) {
        notifyOfferMaker(offer, "Your offer has expired.");
    }

    private void notifyOfferMaker(Offer offer, String message) {
        String offerMakerUserId = offer.getUserId();
        NotificationPreferencesDto prefs = getNotificationPreferences(offerMakerUserId);

        if (prefs.isEnabled(NotificationType.OFFER)) {
            SendNotificationRequest notification = SendNotificationRequest.builder()
                    .message(message)
                    .userId(offerMakerUserId)
                    .offerId(offer.getId())
                    .notificationType(NotificationType.OFFER)
                    .build();

            kafkaTemplate.send(topic.name(), notification);
        }
    }
}
