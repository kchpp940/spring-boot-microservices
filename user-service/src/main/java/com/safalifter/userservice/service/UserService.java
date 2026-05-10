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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageClient fileStorageClient;
    private final ModelMapper modelMapper;

    public User saveUser(RegisterRequest request) {
        checkForDuplicateUsername(request.getUsername());
        checkForDuplicateEmail(request.getEmail());
        User toSave = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.USER)
                .active(Active.ACTIVE)
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

    public User updateUserById(UserUpdateRequest request, MultipartFile file) {
        User toUpdate = findUserById(request.getId());

        request.setUserDetails(updateUserDetails(toUpdate.getUserDetails(), request.getUserDetails(), file));
        modelMapper.map(request, toUpdate);

        return userRepository.save(toUpdate);
    }

    public void deleteUserById(String id) {
        User toDelete = findUserById(id);
        toDelete.setActive(Active.INACTIVE);
        userRepository.save(toDelete);
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

    private UserDetails updateUserDetails(UserDetails toUpdate, UserDetails request, MultipartFile file) {
        toUpdate = toUpdate == null ? new UserDetails() : toUpdate;

        if (file != null) {
            String oldProfilePicture = toUpdate.getProfilePicture();
            String newProfilePicture = fileStorageClient.uploadImageToFIleSystem(file).getBody();

            if (newProfilePicture != null) {
                toUpdate.setProfilePicture(newProfilePicture);
                safeDeleteFile(oldProfilePicture);
            }
        }

        modelMapper.map(request, toUpdate);

        return toUpdate;
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
