package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.client.adapter.FileStorageClientAdapter;
import com.safalifter.jobservice.dto.FileReferenceRequest;
import com.safalifter.jobservice.dto.FileReferenceResponse;
import com.safalifter.jobservice.exc.NotFoundException;
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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService 文件引用流程集成测试")
class JobServiceFileReferenceTest {

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
        jobService = new JobService(jobRepository, categoryService, modelMapper, fileReferenceCoordinator);

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

    @Nested
    @DisplayName("职位创建流程测试")
    class CreateJobFlowTests {

        @Test
        @DisplayName("创建职位成功：上传图片 -> 保存业务数据 -> 绑定引用")
        void testCreateJob_SuccessFlow_WithImage() {
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
        void testCreateJob_SuccessFlow_WithoutImage() {
            when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
                Job saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", "job-123");
                return saved;
            });

            Job result = jobService.createJob(createRequest, null);

            assertNotNull(result);
            assertNull(result.getImageId());
            verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        }

        @Test
        @DisplayName("创建职位失败：上传图片后数据库保存失败，删除上传的图片")
        void testCreateJob_FailAfterUpload_DeletesUploadedFile() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newImageId = "new-image-456";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
            when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);
            when(jobRepository.save(any(Job.class))).thenThrow(new RuntimeException("Database save failed"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals("Database save failed", exception.getMessage());
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("创建职位失败：绑定引用时出错，只删除图片（bound=false，不解绑）")
        void testCreateJob_FailAtBind_OnlyDeletesNoUnbind() {
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

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals("Bind failed", exception.getMessage());
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("创建职位失败：绑定引用时出错，删除图片（与绑定解绑逻辑无关）")
        void testCreateJob_BindFails_DeleteImageCalled() {
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

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals("Bind failed", exception.getMessage());
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }
    }

    @Nested
    @DisplayName("职位更新与图片替换测试")
    class UpdateJobFlowTests {

        @Test
        @DisplayName("更新职位成功：上传新图片 -> 保存 -> 绑定新引用 -> 清理旧引用")
        void testUpdateJob_SuccessWithImageReplacement() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newImageId = "new-image-456";
            String oldImageId = "old-image-123";

            when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(bindBindResponse(newImageId, "job", "job-123", 1, true))
            );
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(oldImageId, "job", "job-123", 0, true))
            );
            when(fileStorageClient.deleteImageFromFileSystem(oldImageId)).thenReturn(ResponseEntity.ok().build());

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
                    ResponseEntity.ok(bindBindResponse(newImageId, "job", "job-123", 1, true))
            );
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(oldImageId, "job", "job-123", 2, true))
            );

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

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.updateJob(updateRequest, mockFile));

            assertEquals("Database save failed", exception.getMessage());
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
            verify(fileStorageClient, never()).deleteImageFromFileSystem("old-image-123");
        }

        @Test
        @DisplayName("更新职位失败：绑定新引用时出错，只删除新文件，不触动旧文件（bound=false）")
        void testUpdateJob_FailAtNewBind_OnlyDeletesNewFile() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newImageId = "new-image-456";

            when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.updateJob(updateRequest, mockFile));

            assertEquals("Bind failed", exception.getMessage());
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
            verify(fileStorageClient, never()).deleteImageFromFileSystem("old-image-123");
        }
    }

    private FileReferenceResponse bindBindResponse(String fileId, String entityType, String entityId, int refCount, boolean bound) {
        return buildBindResponse(fileId, entityType, entityId, refCount, bound);
    }

    @Nested
    @DisplayName("错误码处理测试 - 通过 JobService 业务入口")
    class ErrorCodeHandlingTests {

        @Test
        @DisplayName("file-storage 返回 404：抛出 FeignException.NotFound，回滚新引用")
        void testCreateJob_BindReference_404_ThrowsNotFound() {
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
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals(404, exception.status());
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("file-storage 返回 409：抛出 FeignException，status=409，回滚新引用")
        void testCreateJob_BindReference_409_ThrowsConflict() {
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
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals(409, exception.status());
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("file-storage 返回 500：抛出 FeignException，status=500，回滚新引用")
        void testCreateJob_BindReference_500_ThrowsServerError() {
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
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals(500, exception.status());
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("解绑返回 404：FileReferenceCoordinator 安全处理，不抛出，仍删除新文件")
        void testCreateJob_UnbindReference_404_SafeHandling() {
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

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals("Bind failed", exception.getMessage());
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("解绑返回 5xx：FileReferenceCoordinator 安全处理，不抛出，仍删除新文件")
        void testCreateJob_UnbindReference_5xx_SafeHandling() {
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

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals("Bind failed", exception.getMessage());
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("上传返回 5xx：直接抛出 FeignException，不创建实体")
        void testCreateJob_UploadImage_5xx_ThrowsException() {
            MultipartFile mockFile = mock(MultipartFile.class);

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenThrow(
                    createFeignException(500, "Upload failed")
            );
            when(categoryService.getCategoryById("category-123")).thenReturn(testCategory);

            FeignException exception = assertThrows(FeignException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            assertEquals(500, exception.status());
            verify(jobRepository, never()).save(any(Job.class));
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }
    }

    @Nested
    @DisplayName("回滚顺序验证测试")
    class RollbackOrderTests {

        @Test
        @DisplayName("创建流程回滚顺序：先解绑新引用，再删除新文件")
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
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
            );

            assertThrows(RuntimeException.class,
                    () -> jobService.createJob(createRequest, mockFile));

            verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
        }

        @Test
        @DisplayName("更新流程回滚顺序：解绑新引用 -> 删除新文件 -> 不触动旧引用")
        void testUpdateJob_RollbackOrder() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newImageId = "new-image-456";
            String oldImageId = "old-image-123";

            when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newImageId));
            when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Bind failed"));
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newImageId, "job", "job-123", 0, true))
            );

            assertThrows(RuntimeException.class,
                    () -> jobService.updateJob(updateRequest, mockFile));

            verify(fileStorageClient).unbindReference(argThat(req -> newImageId.equals(req.getFileId())));
            verify(fileStorageClient, never()).unbindReference(argThat(req -> oldImageId.equals(req.getFileId())));
            verify(fileStorageClient).deleteImageFromFileSystem(newImageId);
            verify(fileStorageClient, never()).deleteImageFromFileSystem(oldImageId);
        }
    }

    @Nested
    @DisplayName("职位删除测试")
    class DeleteJobTests {

        @Test
        @DisplayName("删除职位：先删除业务数据，再清理文件引用")
        void testDeleteJob_OrderOfOperations() {
            String oldImageId = "old-image-123";
            testJob.setImageId(oldImageId);

            when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(oldImageId, "job", "job-123", 0, true))
            );

            jobService.deleteJobById("job-123");

            verify(jobRepository).deleteById("job-123");
            verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(oldImageId);
        }

        @Test
        @DisplayName("删除职位：无图片时只删除业务数据")
        void testDeleteJob_NoImage_OnlyDeletesJob() {
            testJob.setImageId(null);
            when(jobRepository.findById("job-123")).thenReturn(Optional.of(testJob));

            jobService.deleteJobById("job-123");

            verify(jobRepository).deleteById("job-123");
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }

        @Test
        @DisplayName("删除职位：职位不存在时抛出 NotFoundException")
        void testDeleteJob_NotFound_ThrowsException() {
            when(jobRepository.findById("non-existent")).thenReturn(Optional.empty());

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> jobService.deleteJobById("non-existent"));

            assertEquals("Job not found", exception.getMessage());
        }
    }
}
