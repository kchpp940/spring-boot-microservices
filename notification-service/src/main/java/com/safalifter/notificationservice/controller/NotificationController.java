package com.safalifter.notificationservice.controller;

import com.safalifter.notificationservice.model.Notification;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import com.safalifter.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/notification")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @PostMapping("/save")
    public ResponseEntity<Void> save(@RequestBody SendNotificationRequest request) {
        notificationService.save(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/getAllByUserId/{userId}")
    public ResponseEntity<List<Notification>> getAllByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getAllByUserId(userId));
    }

    @GetMapping("/getAllByOfferId/{offerId}")
    public ResponseEntity<List<Notification>> getAllByOfferId(@PathVariable String offerId) {
        return ResponseEntity.ok(notificationService.getAllByOfferId(offerId));
    }
}