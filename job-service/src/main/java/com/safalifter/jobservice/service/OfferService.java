package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.NotificationPreferencesDto;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.NotificationType;
import com.safalifter.jobservice.enums.OfferStateMachine;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.repository.OfferRepository;
import com.safalifter.jobservice.request.notification.SendNotificationRequest;
import com.safalifter.jobservice.request.offer.MakeAnOfferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferService {
    private final OfferRepository offerRepository;
    private final AdvertService advertService;
    private final UserServiceClient userServiceclient;
    private final KafkaTemplate<String, SendNotificationRequest> kafkaTemplate;
    private final NewTopic topic;

    public Offer makeAnOffer(MakeAnOfferRequest request) {
        String userId = getUserById(request.getUserId()).getId();
        Advert advert = advertService.getAdvertById(request.getAdvertId());
        Offer toSave = Offer.builder()
                .userId(userId)
                .advert(advert)
                .offeredPrice(request.getOfferedPrice())
                .status(OfferStatus.OPEN)
                .build();
        offerRepository.save(toSave);

        NotificationPreferencesDto prefs = getNotificationPreferences(advert.getUserId());
        if (prefs.isEnabled(NotificationType.OFFER)) {
            SendNotificationRequest notification = SendNotificationRequest.builder()
                    .message("You have received an offer for your advertising.")
                    .userId(advert.getUserId())
                    .offerId(toSave.getId())
                    .notificationType(NotificationType.OFFER)
                    .build();

            kafkaTemplate.send(topic.name(), notification);
        } else {
            log.info("User {} has disabled OFFER notifications, skipping send", advert.getUserId());
        }

        return toSave;
    }

    public Offer getOfferById(String id) {
        return findOfferById(id);
    }

    public List<Offer> getOffersByAdvertId(String id) {
        Advert advert = advertService.getAdvertById(id);
        return offerRepository.getOffersByAdvertId(advert.getId());
    }

    public List<Offer> getOffersByUserId(String id) {
        String userId = getUserById(id).getId();
        return offerRepository.getOffersByUserId(userId);
    }

    public UserDto getUserById(String id) {
        ResponseEntity<UserDto> response = userServiceclient.getUserById(id);
        return Optional.ofNullable(response)
                .map(ResponseEntity::getBody)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public NotificationPreferencesDto getNotificationPreferences(String userId) {
        ResponseEntity<NotificationPreferencesDto> response = userServiceclient.getNotificationPreferences(userId);
        if (response != null && response.getBody() != null) {
            return response.getBody();
        }
        log.warn("Failed to get notification preferences for userId: {}, using default", userId);
        return NotificationPreferencesDto.createDefault(userId);
    }

    @Transactional
    public Offer acceptOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.validateTransition(offer.getStatus(), OfferStatus.ACCEPTED);
        offer.setStatus(OfferStatus.ACCEPTED);
        Offer saved = offerRepository.save(offer);
        sendOfferStatusNotification(saved, "Your offer has been accepted.");
        return saved;
    }

    @Transactional
    public Offer rejectOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.validateTransition(offer.getStatus(), OfferStatus.REJECTED);
        offer.setStatus(OfferStatus.REJECTED);
        Offer saved = offerRepository.save(offer);
        sendOfferStatusNotification(saved, "Your offer has been rejected.");
        return saved;
    }

    @Transactional
    public Offer withdrawOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.validateTransition(offer.getStatus(), OfferStatus.WITHDRAWN);
        offer.setStatus(OfferStatus.WITHDRAWN);
        Offer saved = offerRepository.save(offer);
        sendOfferStatusNotificationToAdvertOwner(saved, "An offer has been withdrawn.");
        return saved;
    }

    @Transactional
    public Offer expireOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.validateTransition(offer.getStatus(), OfferStatus.EXPIRED);
        offer.setStatus(OfferStatus.EXPIRED);
        Offer saved = offerRepository.save(offer);
        sendOfferStatusNotification(saved, "Your offer has expired.");
        return saved;
    }

    private void sendOfferStatusNotification(Offer offer, String message) {
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

    private void sendOfferStatusNotificationToAdvertOwner(Offer offer, String message) {
        String advertOwnerUserId = offer.getAdvert().getUserId();
        NotificationPreferencesDto prefs = getNotificationPreferences(advertOwnerUserId);
        if (prefs.isEnabled(NotificationType.OFFER)) {
            SendNotificationRequest notification = SendNotificationRequest.builder()
                    .message(message)
                    .userId(advertOwnerUserId)
                    .offerId(offer.getId())
                    .notificationType(NotificationType.OFFER)
                    .build();
            kafkaTemplate.send(topic.name(), notification);
        }
    }

    public void deleteOfferById(String id) {
        offerRepository.deleteById(id);
    }

    public boolean authorizeCheck(String id, String principal) {
        return getUserById(getOfferById(id).getUserId()).getUsername().equals(principal);
    }

    public boolean isAdvertOwner(String offerId, String principal) {
        Offer offer = getOfferById(offerId);
        String advertOwnerId = offer.getAdvert().getUserId();
        UserDto advertOwner = getUserById(advertOwnerId);
        return advertOwner.getUsername().equals(principal);
    }

    protected Offer findOfferById(String id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Offer not found"));
    }
}
