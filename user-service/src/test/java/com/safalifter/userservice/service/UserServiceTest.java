package com.safalifter.userservice.service;

import com.safalifter.userservice.client.FileStorageClient;
import com.safalifter.userservice.enums.Active;
import com.safalifter.userservice.enums.Role;
import com.safalifter.userservice.exc.NotFoundException;
import com.safalifter.userservice.model.User;
import com.safalifter.userservice.model.UserDetails;
import com.safalifter.userservice.repository.UserRepository;
import com.safalifter.userservice.request.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FileStorageClient fileStorageClient;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        UserDetails userDetails = UserDetails.builder()
                .firstName("John")
                .lastName("Doe")
                .profilePicture("old-profile-pic-123")
                .build();

        testUser = User.builder()
                .username("johndoe")
                .email("john@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .active(Active.ACTIVE)
                .userDetails(userDetails)
                .build();
        ReflectionTestUtils.setField(testUser, "id", "user-123");

        updateRequest = new UserUpdateRequest();
        updateRequest.setId("user-123");
        UserDetails requestDetails = new UserDetails();
        requestDetails.setFirstName("Updated");
        updateRequest.setUserDetails(requestDetails);
    }

    @Test
    @DisplayName("updateUserById should update profile picture and delete old one when file is provided")
    void testUpdateUserById_UpdatesProfilePicture_WhenFileIsProvided() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-profile-pic-456";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserById(updateRequest, mockFile);

        verify(fileStorageClient).deleteImageFromFileSystem("old-profile-pic-123");
        assertEquals(newImageId, result.getUserDetails().getProfilePicture());
    }

    @Test
    @DisplayName("updateUserById should not throw exception when old image deletion fails")
    void testUpdateUserById_DoesNotThrow_WhenOldImageDeletionFails() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-profile-pic-456";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(fileStorageClient.deleteImageFromFileSystem("old-profile-pic-123")).thenThrow(new RuntimeException("Delete failed"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = assertDoesNotThrow(() -> userService.updateUserById(updateRequest, mockFile));

        verify(fileStorageClient).deleteImageFromFileSystem("old-profile-pic-123");
        assertEquals(newImageId, result.getUserDetails().getProfilePicture());
    }

    @Test
    @DisplayName("updateUserById should not call delete when old profile picture is null")
    void testUpdateUserById_DoesNotDelete_WhenOldProfilePictureIsNull() {
        testUser.getUserDetails().setProfilePicture(null);
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-profile-pic-456";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getUserDetails().getProfilePicture());
    }

    @Test
    @DisplayName("updateUserById should not call delete when old profile picture is empty string")
    void testUpdateUserById_DoesNotDelete_WhenOldProfilePictureIsEmpty() {
        testUser.getUserDetails().setProfilePicture("");
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-profile-pic-456";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getUserDetails().getProfilePicture());
    }

    @Test
    @DisplayName("updateUserById should not change profile picture when upload returns null")
    void testUpdateUserById_DoesNotChangeProfilePicture_WhenUploadReturnsNull() {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(null));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals("old-profile-pic-123", result.getUserDetails().getProfilePicture());
    }

    @Test
    @DisplayName("getUserById should return user when found")
    void testGetUserById_ReturnsUser_WhenFound() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        User result = userService.getUserById("user-123");

        assertEquals(testUser, result);
    }

    @Test
    @DisplayName("getUserById should throw NotFoundException when not found")
    void testGetUserById_ThrowsNotFoundException_WhenNotFound() {
        when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserById("non-existent"));
    }
}
