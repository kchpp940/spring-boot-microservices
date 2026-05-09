package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.AdvertStatus;
import com.safalifter.jobservice.enums.Advertiser;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.AdvertRepository;
import com.safalifter.jobservice.request.advert.AdvertCreateRequest;
import com.safalifter.jobservice.request.advert.AdvertUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvertServiceTest {

    @Mock
    private AdvertRepository advertRepository;

    @Mock
    private JobService jobService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private FileStorageClient fileStorageClient;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AdvertService advertService;

    private Job testJob;
    private UserDto testUser;
    private Advert testAdvert;
    private AdvertCreateRequest createRequest;
    private AdvertUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        testJob = Job.builder()
                .name("Test Job")
                .build();
        ReflectionTestUtils.setField(testJob, "id", "job-123");

        testUser = new UserDto();
        testUser.setId("user-123");
        testUser.setUsername("testuser");

        testAdvert = Advert.builder()
                .userId("user-123")
                .job(testJob)
                .name("Test Advert")
                .advertiser(Advertiser.PROVIDER)
                .deliveryTime("1 day")
                .description("Test description")
                .price(100.0)
                .status(AdvertStatus.OPEN)
                .imageId("old-image-123")
                .build();
        ReflectionTestUtils.setField(testAdvert, "id", "advert-123");

        createRequest = new AdvertCreateRequest();
        createRequest.setUserId("user-123");
        createRequest.setJobId("job-123");
        createRequest.setName("New Advert");
        createRequest.setAdvertiser(Advertiser.PROVIDER);
        createRequest.setDeliveryTime("2 days");
        createRequest.setDescription("New description");
        createRequest.setPrice(200.0);

        updateRequest = new AdvertUpdateRequest();
        updateRequest.setId("advert-123");
        updateRequest.setName("Updated Advert");
    }

    @Test
    @DisplayName("createAdvert should delete uploaded file when database save fails")
    void testCreateAdvert_DeletesUploadedFile_WhenDatabaseSaveFails() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String uploadedFileId = "new-image-456";

        when(userServiceClient.getUserById("user-123")).thenReturn(ResponseEntity.ok(testUser));
        when(jobService.getJobById("job-123")).thenReturn(testJob);
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(uploadedFileId));
        when(advertRepository.save(any(Advert.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> advertService.createAdvert(createRequest, mockFile));

        verify(fileStorageClient).deleteImageFromFileSystem(uploadedFileId);
    }

    @Test
    @DisplayName("createAdvert should not call delete when no file is provided")
    void testCreateAdvert_DoesNotCallDelete_WhenNoFileProvided() {
        when(userServiceClient.getUserById("user-123")).thenReturn(ResponseEntity.ok(testUser));
        when(jobService.getJobById("job-123")).thenReturn(testJob);
        when(advertRepository.save(any(Advert.class))).thenReturn(testAdvert);

        Advert result = advertService.createAdvert(createRequest, null);

        verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertNotNull(result);
    }

    @Test
    @DisplayName("updateAdvertById should update image and delete old one when file is provided")
    void testUpdateAdvertById_UpdatesImage_WhenFileIsProvided() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(advertRepository.save(any(Advert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Advert result = advertService.updateAdvertById(updateRequest, mockFile);

        verify(fileStorageClient).deleteImageFromFileSystem("old-image-123");
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateAdvertById should not throw exception when old image deletion fails")
    void testUpdateAdvertById_DoesNotThrow_WhenOldImageDeletionFails() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(fileStorageClient.deleteImageFromFileSystem("old-image-123")).thenThrow(new RuntimeException("Delete failed"));
        when(advertRepository.save(any(Advert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Advert result = assertDoesNotThrow(() -> advertService.updateAdvertById(updateRequest, mockFile));

        verify(fileStorageClient).deleteImageFromFileSystem("old-image-123");
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateAdvertById should not call delete when old imageId is null")
    void testUpdateAdvertById_DoesNotDelete_WhenOldImageIdIsNull() {
        testAdvert.setImageId(null);
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(advertRepository.save(any(Advert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Advert result = advertService.updateAdvertById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateAdvertById should not call delete when old imageId is empty string")
    void testUpdateAdvertById_DoesNotDelete_WhenOldImageIdIsEmpty() {
        testAdvert.setImageId("");
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(advertRepository.save(any(Advert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Advert result = advertService.updateAdvertById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateAdvertById should not change image when upload returns null")
    void testUpdateAdvertById_DoesNotChangeImage_WhenUploadReturnsNull() {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(null));
        when(advertRepository.save(any(Advert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Advert result = advertService.updateAdvertById(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals("old-image-123", result.getImageId());
    }

    @Test
    @DisplayName("deleteAdvertById should delete associated image")
    void testDeleteAdvertById_DeletesAssociatedImage() {
        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));

        advertService.deleteAdvertById("advert-123");

        verify(fileStorageClient).deleteImageFromFileSystem("old-image-123");
        verify(advertRepository).deleteById("advert-123");
    }

    @Test
    @DisplayName("getAdvertById should return advert when found")
    void testGetAdvertById_ReturnsAdvert_WhenFound() {
        when(advertRepository.findById("advert-123")).thenReturn(Optional.of(testAdvert));

        Advert result = advertService.getAdvertById("advert-123");

        assertEquals(testAdvert, result);
    }

    @Test
    @DisplayName("getAdvertById should throw NotFoundException when not found")
    void testGetAdvertById_ThrowsNotFoundException_WhenNotFound() {
        when(advertRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> advertService.getAdvertById("non-existent"));
    }
}
