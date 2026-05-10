package com.safalifter.jobservice.controller;

import com.safalifter.jobservice.dto.OfferDto;
import com.safalifter.jobservice.request.offer.MakeAnOfferRequest;
import com.safalifter.jobservice.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/job-service/offer")
@RequiredArgsConstructor
public class OfferController {
    private final OfferService offerService;
    private final ModelMapper modelMapper;

    @PostMapping("/makeAnOffer")
    public ResponseEntity<OfferDto> makeAnOffer(@Valid @RequestBody MakeAnOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(modelMapper.map(offerService.makeAnOffer(request), OfferDto.class));
    }

    @GetMapping("/getOfferById/{id}")
    public ResponseEntity<OfferDto> getOfferById(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(offerService.getOfferById(id), OfferDto.class));
    }

    @GetMapping("/getOffersByUserId/{id}")
    public ResponseEntity<List<OfferDto>> getOffersByUserId(@PathVariable String id) {
        return ResponseEntity.ok(offerService.getOffersByUserId(id).stream()
                .map(offer -> modelMapper.map(offer, OfferDto.class)).toList());
    }

    @GetMapping("/getOffersByAdvertId/{id}")
    public ResponseEntity<List<OfferDto>> getOffersByAdvertId(@PathVariable String id) {
        return ResponseEntity.ok(offerService.getOffersByAdvertId(id).stream()
                .map(offer -> modelMapper.map(offer, OfferDto.class)).toList());
    }

    @PostMapping("/accept/{id}")
    @PreAuthorize("hasRole('ADMIN') or @offerService.isAdvertOwner(#id, principal)")
    public ResponseEntity<OfferDto> acceptOffer(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(offerService.acceptOffer(id), OfferDto.class));
    }

    @PostMapping("/reject/{id}")
    @PreAuthorize("hasRole('ADMIN') or @offerService.isAdvertOwner(#id, principal)")
    public ResponseEntity<OfferDto> rejectOffer(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(offerService.rejectOffer(id), OfferDto.class));
    }

    @PostMapping("/withdraw/{id}")
    @PreAuthorize("hasRole('ADMIN') or @offerService.authorizeCheck(#id, principal)")
    public ResponseEntity<OfferDto> withdrawOffer(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(offerService.withdrawOffer(id), OfferDto.class));
    }

    @PostMapping("/expire/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OfferDto> expireOffer(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(offerService.expireOffer(id), OfferDto.class));
    }

    @DeleteMapping("/deleteOfferById/{id}")
    @PreAuthorize("hasRole('ADMIN') or @offerService.authorizeCheck(#id, principal)")
    public ResponseEntity<Void> deleteOfferById(@PathVariable String id) {
        offerService.deleteOfferById(id);
        return ResponseEntity.ok().build();
    }
}
