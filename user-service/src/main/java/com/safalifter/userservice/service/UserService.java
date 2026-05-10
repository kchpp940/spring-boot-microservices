package com.safalifter.userservice.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final FileReferenceCoordinator fileReferenceCoordinator;

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

        return fileReferenceCoordinator.executeUpdate(
                ENTITY_TYPE_USER,
                oldProfilePicture,
                file,
                newFileId -> {
                    request.setUserDetails(updateUserDetails(
                            toUpdate.getUserDetails(),
                            request.getUserDetails(),
                            newFileId));
                    modelMapper.map(request, toUpdate);
                    return userRepository.save(toUpdate);
                },
                User::getId
        );
    }

    @Transactional
    public void deleteUserById(String id) {
        User toDelete = findUserById(id);
        String profilePicture = toDelete.getUserDetails() != null
                ? toDelete.getUserDetails().getProfilePicture()
                : null;

        fileReferenceCoordinator.executeDelete(
                ENTITY_TYPE_USER,
                id,
                profilePicture,
                () -> {
                    toDelete.setActive(Active.INACTIVE);
                    userRepository.save(toDelete);
                }
        );
    }

    @Transactional
    public void hardDeleteUserById(String id) {
        User toDelete = findUserById(id);
        String profilePicture = toDelete.getUserDetails() != null
                ? toDelete.getUserDetails().getProfilePicture()
                : null;

        fileReferenceCoordinator.executeDelete(
                ENTITY_TYPE_USER,
                id,
                profilePicture,
                () -> userRepository.delete(toDelete)
        );
        log.info("User hard deleted: {}", id);
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
}
