package com.safalifter.userservice.service;

import com.safalifter.userservice.client.FileStorageClient;
import com.safalifter.userservice.enums.Active;
import com.safalifter.userservice.enums.Role;
import com.safalifter.userservice.exc.DuplicateResourceException;
import com.safalifter.userservice.exc.NotFoundException;
import com.safalifter.userservice.model.NotificationPreferences;
import com.safalifter.userservice.model.User;
import com.safalifter.userservice.model.UserDetails;
import com.safalifter.userservice.repository.UserRepository;
import com.safalifter.userservice.request.NotificationPreferencesUpdateRequest;
import com.safalifter.userservice.request.RegisterRequest;
import com.safalifter.userservice.request.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageClient fileStorageClient;
    private final ModelMapper modelMapper;

    private static final String ENTITY_TYPE_USER = "user";

    public User saveUser(RegisterRequest request) {
        checkForDuplicateUsername(request.getUsername());
        checkForDuplicateEmail(request.getEmail());
        User toSave = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.USER)
                .active(Active.ACTIVE)
                .userDetails(new UserDetails())
                .notificationPreferences(new NotificationPreferences())
                .build();
        return userRepository.save(toSave);
    }

    private void checkForDuplicateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("username", username);
        }
    }

    private void checkForDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("email", email);
        }
    }

    public List<User> getAll() {
        return userRepository.findAllByActive(Active.ACTIVE);
    }

    public User getUserById(String id) {
        return findUserById(id);
    }

    public User getUserByEmail(String email) {
        return findUserByEmail(email);
    }

    public User getUserByUsername(String username) {
        return findUserByUsername(username);
    }

    @Transactional
    public User updateUserById(UserUpdateRequest request, MultipartFile file) {
        User toUpdate = findUserById(request.getId());
        
        String oldProfilePicture = toUpdate.getUserDetails() != null 
                ? toUpdate.getUserDetails().getProfilePicture() 
                : null;
        String newProfilePicture = null;
        boolean newImageBound = false;

        try {
            if (file != null) {
                newProfilePicture = fileStorageClient.uploadImageToFIleSystem(file).getBody();
                if (newProfilePicture != null) {
                    bindNewProfilePicture(toUpdate.getId(), newProfilePicture);
                    newImageBound = true;
                }
            }

            request.setUserDetails(updateUserDetails(toUpdate.getUserDetails(), request.getUserDetails(), newProfilePicture));
            modelMapper.map(request, toUpdate);

            User savedUser = userRepository.save(toUpdate);

            if (file != null && newProfilePicture != null && oldProfilePicture != null 
                    && !oldProfilePicture.trim().isEmpty() && !oldProfilePicture.equals(newProfilePicture)) {
                try {
                    safeUnbindAndDeleteProfilePicture(toUpdate.getId(), oldProfilePicture);
                } catch (Exception e) {
                    log.warn("Failed to unbind old profile picture after update for user: {}", toUpdate.getId(), e);
                }
            }

            return savedUser;
        } catch (Exception e) {
            if (newProfilePicture != null) {
                if (newImageBound) {
                    try {
                        safeUnbindProfilePicture(toUpdate.getId(), newProfilePicture);
                    } catch (Exception ex) {
                        log.warn("Failed to rollback bind for new profile picture: {}", newProfilePicture, ex);
                    }
                }
                rollbackUploadedFile(newProfilePicture);
            }
            throw e;
        }
    }

    @Transactional
    public void deleteUserById(String id) {
        User toDelete = findUserById(id);
        
        String profilePicture = toDelete.getUserDetails() != null 
                ? toDelete.getUserDetails().getProfilePicture() 
                : null;

        toDelete.setActive(Active.INACTIVE);
        userRepository.save(toDelete);

        if (profilePicture != null && !profilePicture.trim().isEmpty()) {
            try {
                safeUnbindAndDeleteProfilePicture(id, profilePicture);
            } catch (Exception e) {
                log.warn("Failed to cleanup profile picture for user: {}", id, e);
            }
        }
    }

    public void hardDeleteUserById(String id) {
        User toDelete = findUserById(id);
        String profilePicture = toDelete.getUserDetails() != null
                ? toDelete.getUserDetails().getProfilePicture()
                : null;
        userRepository.delete(toDelete);
        log.info("User hard deleted: {}", id);

        if (profilePicture != null && !profilePicture.trim().isEmpty()) {
            try {
                safeUnbindAndDeleteProfilePicture(id, profilePicture);
            } catch (Exception e) {
                log.warn("Failed to cleanup profile picture for hard-deleted user: {}", id, e);
            }
        }
    }

    public NotificationPreferences getNotificationPreferences(String userId) {
        User user = findUserById(userId);
        NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) {
            prefs = new NotificationPreferences();
            user.setNotificationPreferences(prefs);
            userRepository.save(user);
        }
        return prefs;
    }

    public NotificationPreferences updateNotificationPreferences(NotificationPreferencesUpdateRequest request) {
        User user = findUserById(request.getUserId());
        NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) {
            prefs = new NotificationPreferences();
            user.setNotificationPreferences(prefs);
        }
        if (request.getPreferences() != null) {
            request.getPreferences().forEach(prefs::setPreference);
        }
        userRepository.save(user);
        return prefs;
    }

    protected User findUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    protected User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    protected User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserDetails updateUserDetails(UserDetails toUpdate, UserDetails request, String newProfilePicture) {
        toUpdate = toUpdate == null ? new UserDetails() : toUpdate;

        if (newProfilePicture != null) {
            toUpdate.setProfilePicture(newProfilePicture);
        }

        modelMapper.map(request, toUpdate);

        return toUpdate;
    }

    private void safeUnbindProfilePicture(String userId, String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, String> unbindRequest = new HashMap<>();
            unbindRequest.put("fileId", fileId);
            unbindRequest.put("entityType", ENTITY_TYPE_USER);
            unbindRequest.put("entityId", userId);
            fileStorageClient.unbindReference(unbindRequest);
            log.info("Successfully unbound profile picture reference: {} for user: {}", fileId, userId);
        } catch (Exception e) {
            log.warn("Failed to unbind profile picture: {} for user: {}", fileId, userId, e);
        }
    }

    private void bindNewProfilePicture(String userId, String fileId) {
        try {
            Map<String, String> bindRequest = new HashMap<>();
            bindRequest.put("fileId", fileId);
            bindRequest.put("entityType", ENTITY_TYPE_USER);
            bindRequest.put("entityId", userId);
            fileStorageClient.bindReference(bindRequest);
            log.info("Successfully bound profile picture reference for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to bind profile picture reference for user: {}", userId, e);
            throw e;
        }
    }

    private void safeUnbindAndDeleteProfilePicture(String userId, String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, String> unbindRequest = new HashMap<>();
            unbindRequest.put("fileId", fileId);
            unbindRequest.put("entityType", ENTITY_TYPE_USER);
            unbindRequest.put("entityId", userId);
            
            var response = fileStorageClient.unbindReference(unbindRequest);
            Map<String, Object> body = response.getBody();
            
            if (body != null && Boolean.TRUE.equals(body.get("unbound"))) {
                Integer newCount = (Integer) body.get("referenceCount");
                if (newCount != null && newCount == 0) {
                    try {
                        fileStorageClient.deleteImageFromFileSystem(fileId);
                        log.info("Successfully deleted profile picture for user: {}", userId);
                    } catch (Exception e) {
                        log.warn("Failed to delete profile picture after unbind for user: {}", userId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to unbind profile picture for user: {}", userId, e);
        }
    }

    private void rollbackUploadedFile(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }
        try {
            fileStorageClient.deleteImageFromFileSystem(fileId);
            log.info("Rolled back uploaded file: {}", fileId);
        } catch (Exception e) {
            log.warn("Failed to rollback uploaded file: {}", fileId, e);
        }
    }

    private void safeDeleteFile(String fileId) {
        if (fileId != null && !fileId.trim().isEmpty()) {
            try {
                fileStorageClient.deleteImageFromFileSystem(fileId);
            } catch (Exception e) {
                log.warn("Failed to delete file with id: {}", fileId, e);
            }
        }
    }
}
