package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.client.adapter.FileStorageClientAdapter;
import com.safalifter.jobservice.dto.FileReferenceRequest;
import com.safalifter.jobservice.dto.FileReferenceResponse;
import com.safalifter.jobservice.model.Category;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.JobRepository;
import com.safalifter.jobservice.request.job.JobCreateRequest;
import com.safalifter.jobservice.request.job.JobUpdateRequest;
import feign.FeignException;
import feign.Request;
import feign.Response;
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

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileReferenceIntegrationTest {

    @Mock
    private FileStorageClient fileStorageClient;

    @InjectMocks
    private FileStorageClientAdapter fileStorageClientAdapter;

    private FileReferenceCoordinator fileReferenceCoordinator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ModelMapper modelMapper;

    private JobService jobService;

    private Job testJob;
    private Category testCategory;
    private JobCreateRequest createRequest;
    private JobUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        fileReferenceCoordinator = new FileReferenceCoordinator(fileStorageClientAdapter);

        testCategory = Category.builder()
                .name("Test Category")
                .description("Test Description")
                .build();
        ReflectionTestUtils.setField(testCategory, "id", "category-123");

        testJob = Job.builder()
                .name("Test Job")
                .description("Test Job Description")
                .category(testCategory)
                .imageId("old-image-123")
                .build();
        ReflectionTestUtils.setField(testJob, "id", "job-123");

        createRequest = new JobCreateRequest();
        createRequest.setCategoryId("category-123");
        createRequest.setName("New Job");
        createRequest.setDescription("Description");
        createRequest.setKeys(new String[]{"key1", "key2"});

        updateRequest = new JobUpdateRequest();
        updateRequest.setId("job-123");
        updateRequest.setName("Updated Job");
    }

    private JobService createJobService() {
        return new JobService(jobRepository, categoryService, modelMapper, fileReferenceCoordinator);
    }

    private FileReferenceResponse buildBindResponse(String fileId, String entityType, String entityId, int refCount, boolean bound) {
        return FileReferenceResponse.builder()
                .fileId(fileId)
                .entityType(entityType)
                .entityId(entityId)
                .referenceCount(refCount)
                .bound(bound)
                .build();
    }

    private FileReferenceResponse buildUnbindResponse(String fileId, String entityType, String entityId, int refCount, boolean unbound) {
        return FileReferenceResponse.builder()
                .fileId(fileId)
                .entityType(entityType)
                .entityId(entityId)
                .referenceCount(refCount)
                .unbound(unbound)
                .build();
    }

    private FeignException createFeignException(int status, String message) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://file-storage/test",
                Collections.emptyMap(),
                null,
                null,
                null
        );
        Response response = Response.builder()
                .status(status)
                .reason(message)
                .request(request)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("testMethod", response);
    }

    @Test
    @DisplayName("创建职位成功：上传图片 -> 保存业务数据 -> 绑定引用")
    void testCreateJob_SuccessFlow() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildBindResponse(newImageId, "job", "job-123", 1, true))
        );

        jobService = createJobService();
        Job result = jobService.createJob(createRequest, mockFile);

        assertNotNull(result);
        assertEquals(newImageId, result.getImageId());
        verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
        verify(jobRepository).save(any(Job.class));
        verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
    }

    @Test
    @DisplayName("创建职位成功：无图片时跳过上传和绑定")
    void testCreateJob_SuccessWithoutImage() {
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });

        jobService = createJobService();
        Job result = jobService.createJob(createRequest, null);

        assertNotNull(result);
        assertNull(result.getImageId());
        verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
    }

    @Test
    @DisplayName("创建职位失败：上传图片后数据库保存失败，应删除上传的图片")
    void testCreateJob_FailAfterUpload_DeletesUploadedFile() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenThrow(new RuntimeException("Database save failed"));

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));

        verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("创建职位失败：绑定引用后出错，应解绑并删除图片")
    void testCreateJob_FailAfterBind_UnbindsAndDeletes() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));

        verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
        verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("创建职位失败：解绑失败时仍尝试删除图片")
    void testCreateJob_UnbindFails_StillTriesDelete() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Unbind failed"));

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));

        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("更新职位成功：上传新图片 -> 保存业务数据 -> 绑定新引用 -> 清理旧引用")
    void testUpdateJob_SuccessWithImageReplacement() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";
        String oldImageId = "old-image-123";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildBindResponse(newImageId, "job", "job-123", 1, true))
        );
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildUnbindResponse(oldImageId, "job", "job-123", 0, true))
        );
        when(fileStorageClient.deleteImageFromFileSystem(oldImageId)).thenReturn(ResponseEntity.ok().build());

        jobService = createJobService();
        Job result = jobService.updateJob(updateRequest, mockFile);

        assertNotNull(result);
        assertEquals(newImageId, result.getImageId());
        verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
        verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(oldImageId);
    }

    @Test
    @DisplayName("更新职位成功：旧图片被其他实体引用时，解绑但不删除")
    void testUpdateJob_Success_OldImageStillReferenced() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";
        String oldImageId = "old-image-123";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildBindResponse(newImageId, "job", "job-123", 1, true))
        );
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildUnbindResponse(oldImageId, "job", "job-123", 2, true))
        );

        jobService = createJobService();
        Job result = jobService.updateJob(updateRequest, mockFile);

        assertNotNull(result);
        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(oldImageId);
    }

    @Test
    @DisplayName("更新职位成功：无新图片时跳过上传，不修改图片引用")
    void testUpdateJob_SuccessWithoutNewImage() {
        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobService = createJobService();
        Job result = jobService.updateJob(updateRequest, null);

        assertNotNull(result);
        assertEquals("old-image-123", result.getImageId());
        verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
    }

    @Test
    @DisplayName("更新职位失败：上传新图片后保存失败，删除新图片不影响旧图片")
    void testUpdateJob_FailAfterUpload_DeletesNewFileOnly() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenThrow(new RuntimeException("Database save failed"));

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.updateJob(updateRequest, mockFile));

        verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        verify(fileStorageClient, never()).deleteImageFromFileSystem("old-image-123");
    }

    @Test
    @DisplayName("更新职位失败：绑定新引用后出错，回滚新引用，保留旧引用")
    void testUpdateJob_FailAfterNewBind_RollbacksNewOnly() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.updateJob(updateRequest, mockFile));

        verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("绑定引用返回 404（文件不存在）：应抛出异常并回滚")
    void testBindReference_404_FileNotFound() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                createFeignException(404, "File not found")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("绑定引用返回 409（冲突）：应抛出异常并回滚")
    void testBindReference_409_Conflict() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                createFeignException(409, "Conflict")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("绑定引用返回 500（服务端错误）：应抛出异常并回滚")
    void testBindReference_500_ServerError() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                createFeignException(500, "Internal server error")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("解绑引用返回 404：FileReferenceCoordinator 应安全处理，不抛出异常")
    void testUnbindReference_404_SafeHandling() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(
                createFeignException(404, "Reference not found")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("解绑引用返回 5xx：FileReferenceCoordinator 应安全处理，不抛出异常")
    void testUnbindReference_5xx_SafeHandling() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(
                createFeignException(503, "Service unavailable")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("删除图片返回 404：FileReferenceCoordinator 应安全处理")
    void testDeleteImage_404_SafeHandling() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenThrow(new RuntimeException("Database save failed"));
        when(fileStorageClient.deleteImageFromFileSystem(newImageId)).thenThrow(
                createFeignException(404, "File not found")
        );

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
    }

    @Test
    @DisplayName("上传图片返回 5xx：应直接抛出异常")
    void testUploadImage_5xx_ThrowsException() {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenThrow(
                createFeignException(500, "Upload failed")
        );
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));
        verify(jobRepository, never()).save(any(Job.class));
        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
    }

    @Test
    @DisplayName("验证创建流程回滚顺序：先解绑新引用，再删除新文件")
    void testCreateJob_RollbackOrder() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";

        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "job-123");
            return saved;
        });
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.createJob(createRequest, mockFile));

        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
    }

    @Test
    @DisplayName("验证更新流程回滚顺序：解绑新引用 -> 删除新文件 -> 不触动旧引用")
    void testUpdateJob_RollbackOrder() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String newImageId = "new-image-456";
        String oldImageId = "old-image-123";

        when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
        when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));

        jobService = createJobService();
        assertThrows(RuntimeException.class, () -> jobService.updateJob(updateRequest, mockFile));

        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient, never()).deleteImageFromFileSystem(oldImageId);
    }

    @Test
    @DisplayName("bindReference：空参数时返回 null")
    void testBindReference_NullFileId_ReturnsNull() {
        FileReferenceResponse result = fileReferenceCoordinator.bindReference("job", "entity-123", null);
        assertNull(result);

        result = fileReferenceCoordinator.bindReference(null, "entity-123", "file-123");
        assertNull(result);

        result = fileReferenceCoordinator.bindReference("job", null, "file-123");
        assertNull(result);

        verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
    }

    @Test
    @DisplayName("safeUnbindReference：异常时返回 null 不抛出")
    void testSafeUnbindReference_Exception_ReturnsNull() {
        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Unbind failed"));

        FileReferenceResponse result = fileReferenceCoordinator.safeUnbindReference("job", "entity-123", "file-123");
        assertNull(result);
    }

    @Test
    @DisplayName("safeDeleteFile：异常时不抛出")
    void testSafeDeleteFile_Exception_NoThrow() {
        when(fileStorageClient.deleteImageFromFileSystem("file-123")).thenThrow(new RuntimeException("Delete failed"));
        assertDoesNotThrow(() -> fileReferenceCoordinator.safeDeleteFile("file-123"));
    }

    @Test
    @DisplayName("executeDelete：先执行业务删除，再清理文件引用")
    void testExecuteDelete_OrderOfOperations() {
        String fileId = "test-file-123";
        String entityId = "entity-123";
        Runnable deletionAction = mock(Runnable.class);

        when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                ResponseEntity.ok(buildUnbindResponse(fileId, "job", entityId, 0, true))
        );

        fileReferenceCoordinator.executeDelete("job", entityId, fileId, deletionAction);

        verify(deletionAction).run();
        verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
        verify(fileStorageClient).deleteImageFromFileSystem(fileId);
    }
}
