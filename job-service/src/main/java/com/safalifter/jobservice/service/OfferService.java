package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.adapter.UserServiceClientAdapter;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.OfferStateMachine;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.repository.OfferRepository;
import com.safalifter.jobservice.request.offer.MakeAnOfferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferService {
    private final OfferRepository offerRepository;
    private final AdvertService advertService;
    private final UserServiceClientAdapter userServiceClientAdapter;
    private final OfferNotificationService offerNotificationService;

    public Offer makeAnOffer(MakeAnOfferRequest request) {
        String userId = getUserById(request.getUserId()).getId();
        Advert advert = advertService.getAdvertById(request.getAdvertId());
        Offer toSave = Offer.builder()
                .userId(userId)
                .advert(advert)
                .offeredPrice(request.getOfferedPrice())
                .status(OfferStatus.OPEN)
                .build();
        Offer saved = offerRepository.save(toSave);

        offerNotificationService.notifyAdvertOwnerOfNewOffer(saved);

        return saved;
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
        return userServiceClientAdapter.getUserByIdOrThrow(id);
    }

    @Transactional
    public Offer acceptOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.transition(offer, OfferStatus.ACCEPTED);
        Offer saved = offerRepository.save(offer);
        offerNotificationService.notifyOfferMakerOfAcceptance(saved);
        return saved;
    }

    @Transactional
    public Offer rejectOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.transition(offer, OfferStatus.REJECTED);
        Offer saved = offerRepository.save(offer);
        offerNotificationService.notifyOfferMakerOfRejection(saved);
        return saved;
    }

    @Transactional
    public Offer withdrawOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.transition(offer, OfferStatus.WITHDRAWN);
        Offer saved = offerRepository.save(offer);
        offerNotificationService.notifyAdvertOwnerOfWithdrawal(saved);
        return saved;
    }

    @Transactional
    public Offer expireOffer(String offerId) {
        Offer offer = findOfferById(offerId);
        OfferStateMachine.transition(offer, OfferStatus.EXPIRED);
        Offer saved = offerRepository.save(offer);
        offerNotificationService.notifyOfferMakerOfExpiration(saved);
        return saved;
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
