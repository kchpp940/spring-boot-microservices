package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Category;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.JobRepository;
import com.safalifter.jobservice.request.job.JobUpdateRequest;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private FileStorageClient fileStorageClient;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private JobService jobService;

    private Job testJob;
    private Category testCategory;
    private Category newCategory;
    private JobUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .name("Test Category")
                .description("Test Description")
                .build();
        ReflectionTestUtils.setField(testCategory, "id", "category-123");

        newCategory = Category.builder()
                .name("New Category")
                .description("New Description")
                .build();
        ReflectionTestUtils.setField(newCategory, "id", "category-456");

        testJob = Job.builder()
                .name("Test Job")
                .description("Test Job Description")
                .category(testCategory)
                .imageId("old-image-123")
                .build();
        ReflectionTestUtils.setField(testJob, "id", "job-123");

        updateRequest = new JobUpdateRequest();
        updateRequest.setId("job-123");
        updateRequest.setName("Updated Job");
    }

    @Test
    @DisplayName("updateJob should use request.getId() to find job, not request.getCategoryId()")
    void testUpdateJob_UsesIdNotCategoryId() {
        updateRequest.setCategoryId("different-category");

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenReturn(testJob);

        Job result = jobService.updateJob(updateRequest, null);

        verify(jobRepository).findById("job-123");
        verify(jobRepository, never()).findById("different-category");
        assertNotNull(result);
    }

    @Test
    @DisplayName("updateJob should throw NotFoundException when job not found by id")
    void testUpdateJob_ThrowsNotFoundException_WhenJobNotFound() {
        updateRequest.setId("non-existent-id");
        updateRequest.setCategoryId("category-123");

        when(jobRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobService.updateJob(updateRequest, null));
        verify(jobRepository).findById("non-existent-id");
    }

    @Test
    @DisplayName("updateJob should validate and update category when categoryId changes")
    void testUpdateJob_UpdatesCategory_WhenCategoryIdChanges() {
        updateRequest.setCategoryId("category-456");

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(categoryService.getCategoryById("category-456")).thenReturn(newCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, null);

        verify(categoryService).getCategoryById("category-456");
        assertEquals(newCategory, result.getCategory());
    }

    @Test
    @DisplayName("updateJob should throw NotFoundException when categoryId refers to non-existent category")
    void testUpdateJob_ThrowsNotFoundException_WhenCategoryNotFound() {
        updateRequest.setCategoryId("non-existent-category");

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(categoryService.getCategoryById("non-existent-category"))
                .thenThrow(new NotFoundException("Category not found"));

        assertThrows(NotFoundException.class, () -> jobService.updateJob(updateRequest, null));
        verify(categoryService).getCategoryById("non-existent-category");
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    @DisplayName("updateJob should not change category when categoryId is null")
    void testUpdateJob_DoesNotChangeCategory_WhenCategoryIdIsNull() {
        updateRequest.setCategoryId(null);

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, null);

        verify(categoryService, never()).getCategoryById(anyString());
        assertEquals(testCategory, result.getCategory());
    }

    @Test
    @DisplayName("updateJob should not change category when categoryId is same as current")
    void testUpdateJob_DoesNotChangeCategory_WhenCategoryIdIsSame() {
        updateRequest.setCategoryId("category-123");

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, null);

        verify(categoryService, never()).getCategoryById(anyString());
        assertEquals(testCategory, result.getCategory());
    }

    @Test
    @DisplayName("updateJob should set category when original job has no category but request provides categoryId")
    void testUpdateJob_SetsCategory_WhenOriginalJobHasNoCategory() {
        Job jobWithoutCategory = Job.builder()
                .name("Test Job")
                .description("Test Job Description")
                .category(null)
                .imageId("old-image-123")
                .build();
        ReflectionTestUtils.setField(jobWithoutCategory, "id", "job-123");

        updateRequest.setCategoryId("category-123");

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(jobWithoutCategory));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, null);

        verify(categoryService).getCategoryById("category-123");
        assertEquals(testCategory, result.getCategory());
    }

    @Test
    @DisplayName("updateJob should delete old image and set new image when file is provided")
    void testUpdateJob_DeletesOldImageAndSetsNew_WhenFileIsProvided() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(fileStorageClient.deleteImageFromFileSystem("old-image-123")).thenReturn(ResponseEntity.ok().build());
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, mockFile);

        verify(fileStorageClient).deleteImageFromFileSystem("old-image-123");
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateJob should not throw exception when old image deletion fails")
    void testUpdateJob_DoesNotThrow_WhenOldImageDeletionFails() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(fileStorageClient.deleteImageFromFileSystem("old-image-123")).thenThrow(new RuntimeException("Delete failed"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> jobService.updateJob(updateRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem("old-image-123");
    }

    @Test
    @DisplayName("updateJob should not call delete when old imageId is null")
    void testUpdateJob_DoesNotDelete_WhenOldImageIdIsNull() {
        testJob.setImageId(null);
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateJob should not call delete when old imageId is empty string")
    void testUpdateJob_DoesNotDelete_WhenOldImageIdIsEmpty() {
        testJob.setImageId("");
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals(newImageId, result.getImageId());
    }

    @Test
    @DisplayName("updateJob should not set new image when upload returns null")
    void testUpdateJob_DoesNotSetImage_WhenUploadReturnsNull() {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(null));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.updateJob(updateRequest, mockFile);

        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        assertEquals("old-image-123", result.getImageId());
    }

    @Test
    @DisplayName("getJobById should return job when found")
    void testGetJobById_ReturnsJob_WhenFound() {
        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));

        Job result = jobService.getJobById("job-123");

        assertEquals(testJob, result);
    }

    @Test
    @DisplayName("getJobById should throw NotFoundException when not found")
    void testGetJobById_ThrowsNotFoundException_WhenNotFound() {
        when(jobRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobService.getJobById("non-existent"));
    }
}
