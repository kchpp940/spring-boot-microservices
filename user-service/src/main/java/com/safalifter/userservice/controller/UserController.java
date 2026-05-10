package com.safalifter.userservice.controller;

import com.safalifter.userservice.dto.AuthUserDto;
import com.safalifter.userservice.dto.NotificationPreferencesDto;
import com.safalifter.userservice.dto.UserDto;
import com.safalifter.userservice.enums.NotificationType;
import com.safalifter.userservice.model.NotificationPreferences;
import com.safalifter.userservice.request.NotificationPreferencesUpdateRequest;
import com.safalifter.userservice.request.RegisterRequest;
import com.safalifter.userservice.request.UserUpdateRequest;
import com.safalifter.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping("/save")
    public ResponseEntity<UserDto> save(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(modelMapper.map(userService.saveUser(request), UserDto.class));
    }

    @GetMapping("/getAll")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getAll() {
        return ResponseEntity.ok(userService.getAll().stream()
                .map(user -> modelMapper.map(user, UserDto.class)).toList());
    }

    @GetMapping("/getUserById/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(modelMapper.map(userService.getUserById(id), UserDto.class));
    }

    @GetMapping("/getUserByEmail/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(modelMapper.map(userService.getUserByEmail(email), UserDto.class));
    }

    @GetMapping("/getUserByUsername/{username}")
    public ResponseEntity<AuthUserDto> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(modelMapper.map(userService.getUserByUsername(username), AuthUserDto.class));
    }

    @GetMapping("/getUserInfoByUsername/{username}")
    public ResponseEntity<UserDto> getUserInfoByUsername(@PathVariable String username) {
        return ResponseEntity.ok(modelMapper.map(userService.getUserByUsername(username), UserDto.class));
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN') or @userService.getUserById(#request.id).username == principal")
    public ResponseEntity<UserDto> updateUserById(@Valid @RequestPart UserUpdateRequest request,
                                                  @RequestPart(required = false) MultipartFile file) {
        return ResponseEntity.ok(modelMapper.map(userService.updateUserById(request, file), UserDto.class));
    }

    @DeleteMapping("/deleteUserById/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userService.getUserById(#id).username == principal")
    public ResponseEntity<Void> deleteUserById(@PathVariable String id) {
        userService.deleteUserById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/internal/hardDeleteUserById/{id}")
    public ResponseEntity<Void> internalHardDeleteUserById(@PathVariable String id) {
        userService.hardDeleteUserById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notification-preferences/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userService.getUserById(#userId).username == principal")
    public ResponseEntity<NotificationPreferencesDto> getNotificationPreferences(@PathVariable String userId) {
        NotificationPreferences prefs = userService.getNotificationPreferences(userId);
        NotificationPreferencesDto dto = NotificationPreferencesDto.builder()
                .userId(userId)
                .preferences(toMap(prefs))
                .build();
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/notification-preferences")
    @PreAuthorize("hasRole('ADMIN') or @userService.getUserById(#request.userId).username == principal")
    public ResponseEntity<NotificationPreferencesDto> updateNotificationPreferences(
            @Valid @RequestBody NotificationPreferencesUpdateRequest request) {
        NotificationPreferences prefs = userService.updateNotificationPreferences(request);
        NotificationPreferencesDto dto = NotificationPreferencesDto.builder()
                .userId(request.getUserId())
                .preferences(toMap(prefs))
                .build();
        return ResponseEntity.ok(dto);
    }

    private Map<NotificationType, Boolean> toMap(NotificationPreferences prefs) {
        Map<NotificationType, Boolean> map = new EnumMap<>(NotificationType.class);
        map.put(NotificationType.OFFER, prefs.isOfferEnabled());
        map.put(NotificationType.JOB_UPDATE, prefs.isJobUpdateEnabled());
        map.put(NotificationType.SYSTEM_MESSAGE, prefs.isSystemMessageEnabled());
        return map;
    }
}
