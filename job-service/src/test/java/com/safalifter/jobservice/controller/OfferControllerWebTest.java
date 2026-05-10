package com.safalifter.jobservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.jobservice.dto.OfferDto;
import com.safalifter.jobservice.enums.OfferStatus;
import com.safalifter.jobservice.exc.IllegalStateTransitionException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Offer;
import com.safalifter.jobservice.service.OfferService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OfferControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OfferService offerService;

    @MockBean
    private ModelMapper modelMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private Key signKey;

    private String adminToken;
    private String advertOwnerToken;
    private String offerMakerToken;
    private String otherUserToken;

    private String testOfferId;
    private Offer testOffer;
    private OfferDto testOfferDto;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        signKey = Keys.hmacShaKeyFor(keyBytes);

        adminToken = Jwts.builder()
                .setSubject("adminuser")
                .setIssuer("ROLE_ADMIN")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();

        advertOwnerToken = Jwts.builder()
                .setSubject("advertowner")
                .setIssuer("ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();

        offerMakerToken = Jwts.builder()
                .setSubject("offermaker")
                .setIssuer("ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();

        otherUserToken = Jwts.builder()
                .setSubject("otheruser")
                .setIssuer("ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();

        testOfferId = "offer-123";

        Advert advert = Advert.builder()
                .userId("advert-owner-id")
                .name("Test Advert")
                .build();
        ReflectionTestUtils.setField(advert, "id", "advert-123");

        testOffer = Offer.builder()
                .userId("offer-maker-id")
                .offeredPrice(500)
                .status(OfferStatus.OPEN)
                .advert(advert)
                .build();
        ReflectionTestUtils.setField(testOffer, "id", testOfferId);

        testOfferDto = new OfferDto();
        testOfferDto.setId(testOfferId);
        testOfferDto.setStatus("ACCEPTED");
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 广告所有者可以接受 Offer - 200 OK")
    void testAcceptOffer_AdvertOwner_Success200() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "advertowner")).thenReturn(true);
        when(offerService.acceptOffer(testOfferId)).thenReturn(testOffer);
        when(modelMapper.map(testOffer, OfferDto.class)).thenReturn(testOfferDto);

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + advertOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testOfferId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(offerService).acceptOffer(testOfferId);
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - ADMIN 可以接受 Offer - 200 OK")
    void testAcceptOffer_Admin_Success200() throws Exception {
        when(offerService.acceptOffer(testOfferId)).thenReturn(testOffer);
        when(modelMapper.map(testOffer, OfferDto.class)).thenReturn(testOfferDto);

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        verify(offerService).acceptOffer(testOfferId);
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 出价用户不能接受 Offer - 403 Forbidden")
    void testAcceptOffer_OfferMaker_Forbidden403() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "offermaker")).thenReturn(false);

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + offerMakerToken))
                .andExpect(status().isForbidden());

        verify(offerService, never()).acceptOffer(anyString());
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 其他用户不能接受 Offer - 403 Forbidden")
    void testAcceptOffer_OtherUser_Forbidden403() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "otheruser")).thenReturn(false);

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());

        verify(offerService, never()).acceptOffer(anyString());
    }

    @Test
    @DisplayName("POST /offer/reject/{id} - 广告所有者可以拒绝 Offer - 200 OK")
    void testRejectOffer_AdvertOwner_Success200() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "advertowner")).thenReturn(true);
        testOffer.setStatus(OfferStatus.REJECTED);
        when(offerService.rejectOffer(testOfferId)).thenReturn(testOffer);
        testOfferDto.setStatus("REJECTED");
        when(modelMapper.map(testOffer, OfferDto.class)).thenReturn(testOfferDto);

        mockMvc.perform(post("/v1/job-service/offer/reject/" + testOfferId)
                        .header("Authorization", "Bearer " + advertOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(offerService).rejectOffer(testOfferId);
    }

    @Test
    @DisplayName("POST /offer/withdraw/{id} - 出价用户可以撤回 Offer - 200 OK")
    void testWithdrawOffer_OfferMaker_Success200() throws Exception {
        when(offerService.authorizeCheck(testOfferId, "offermaker")).thenReturn(true);
        testOffer.setStatus(OfferStatus.WITHDRAWN);
        when(offerService.withdrawOffer(testOfferId)).thenReturn(testOffer);
        testOfferDto.setStatus("WITHDRAWN");
        when(modelMapper.map(testOffer, OfferDto.class)).thenReturn(testOfferDto);

        mockMvc.perform(post("/v1/job-service/offer/withdraw/" + testOfferId)
                        .header("Authorization", "Bearer " + offerMakerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        verify(offerService).withdrawOffer(testOfferId);
    }

    @Test
    @DisplayName("POST /offer/withdraw/{id} - 广告所有者不能撤回 Offer - 403 Forbidden")
    void testWithdrawOffer_AdvertOwner_Forbidden403() throws Exception {
        when(offerService.authorizeCheck(testOfferId, "advertowner")).thenReturn(false);

        mockMvc.perform(post("/v1/job-service/offer/withdraw/" + testOfferId)
                        .header("Authorization", "Bearer " + advertOwnerToken))
                .andExpect(status().isForbidden());

        verify(offerService, never()).withdrawOffer(anyString());
    }

    @Test
    @DisplayName("POST /offer/expire/{id} - ADMIN 可以过期 Offer - 200 OK")
    void testExpireOffer_Admin_Success200() throws Exception {
        testOffer.setStatus(OfferStatus.EXPIRED);
        when(offerService.expireOffer(testOfferId)).thenReturn(testOffer);
        testOfferDto.setStatus("EXPIRED");
        when(modelMapper.map(testOffer, OfferDto.class)).thenReturn(testOfferDto);

        mockMvc.perform(post("/v1/job-service/offer/expire/" + testOfferId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        verify(offerService).expireOffer(testOfferId);
    }

    @Test
    @DisplayName("POST /offer/expire/{id} - 普通用户不能过期 Offer - 403 Forbidden")
    void testExpireOffer_RegularUser_Forbidden403() throws Exception {
        mockMvc.perform(post("/v1/job-service/offer/expire/" + testOfferId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());

        verify(offerService, never()).expireOffer(anyString());
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 非法状态转换返回 409 Conflict")
    void testAcceptOffer_IllegalStateTransition_Returns409() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "advertowner")).thenReturn(true);
        when(offerService.acceptOffer(testOfferId)).thenThrow(
                new IllegalStateTransitionException("Illegal state transition: ACCEPTED -> ACCEPTED"));

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + advertOwnerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Illegal state transition: ACCEPTED -> ACCEPTED"));
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 并发冲突返回 409 Conflict")
    void testAcceptOffer_OptimisticLock_Returns409() throws Exception {
        when(offerService.isAdvertOwner(testOfferId, "advertowner")).thenReturn(true);
        when(offerService.acceptOffer(testOfferId)).thenThrow(
                new ObjectOptimisticLockingFailureException(Offer.class, testOfferId));

        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId)
                        .header("Authorization", "Bearer " + advertOwnerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Concurrent update detected. Please refresh and try again."));
    }

    @Test
    @DisplayName("POST /offer/accept/{id} - 未登录返回 403 Forbidden")
    void testAcceptOffer_NoToken_Returns403() throws Exception {
        mockMvc.perform(post("/v1/job-service/offer/accept/" + testOfferId))
                .andExpect(status().isForbidden());
    }
}
