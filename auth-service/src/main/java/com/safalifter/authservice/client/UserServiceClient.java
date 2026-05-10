package com.safalifter.authservice.client;

import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.dto.UserDto;
import com.safalifter.authservice.request.RegisterRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", path = "/v1/user")
public interface UserServiceClient {
    @PostMapping("/save")
    ResponseEntity<RegisterDto> save(@RequestBody RegisterRequest request);

    @GetMapping("/getUserByUsername/{username}")
    ResponseEntity<UserDto> getUserByUsername(@PathVariable String username);

    @DeleteMapping("/internal/hardDeleteUserById/{id}")
    ResponseEntity<Void> internalHardDeleteUserById(@PathVariable String id);
}
