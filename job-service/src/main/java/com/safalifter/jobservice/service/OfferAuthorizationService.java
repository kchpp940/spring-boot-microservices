package com.safalifter.jobservice.service;

import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.model.Offer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfferAuthorizationService {

    private final OfferService offerService;

    public boolean isOfferOwner(String offerId, String principal) {
        Offer offer = offerService.getOfferById(offerId);
        String offerOwnerUsername = offerService.getUserById(offer.getUserId()).getUsername();
        return offerOwnerUsername.equals(principal);
    }

    public boolean isAdvertOwner(String offerId, String principal) {
        Offer offer = offerService.getOfferById(offerId);
        String advertOwnerId = offer.getAdvert().getUserId();
        UserDto advertOwner = offerService.getUserById(advertOwnerId);
        return advertOwner.getUsername().equals(principal);
    }
}
