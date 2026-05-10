package com.safalifter.authservice.client;

import com.safalifter.authservice.request.SendNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", path = "/v1/notification")
public interface NotificationServiceClient {
    @PostMapping("/save")
    ResponseEntity<Void> save(@RequestBody SendNotificationRequest request);
}
