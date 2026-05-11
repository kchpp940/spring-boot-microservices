package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.client.adapter.FileStorageClientAdapter;
import com.safalifter.jobservice.dto.FileReferenceRequest;
import com.safalifter.jobservice.dto.FileReferenceResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileReferenceCoordinator 单元测试")
class FileReferenceCoordinatorTest {

    @Mock
    private FileStorageClient fileStorageClient;

    @InjectMocks
    private FileStorageClientAdapter fileStorageClientAdapter;

    private FileReferenceCoordinator fileReferenceCoordinator;

    @BeforeEach
    void setUp() {
        fileReferenceCoordinator = new FileReferenceCoordinator(fileStorageClientAdapter);
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
    @DisplayName("工具方法测试")
    class UtilityMethodTests {

        @Test
        @DisplayName("bindReference：空参数时返回 null，不调用底层服务")
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
        @DisplayName("bindReference：正常调用时返回响应")
        void testBindReference_Success_ReturnsResponse() {
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildBindResponse("file-123", "job", "entity-123", 1, true))
            );

            FileReferenceResponse result = fileReferenceCoordinator.bindReference("job", "entity-123", "file-123");

            assertNotNull(result);
            assertTrue(result.isBound());
            assertEquals(1, result.getReferenceCount());
        }

        @Test
        @DisplayName("bindReference：异常时抛出原始异常")
        void testBindReference_Exception_Throws() {
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(500, "Server error")
            );

            assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.bindReference("job", "entity-123", "file-123"));
        }

        @Test
        @DisplayName("safeUnbindReference：异常时返回 null 不抛出")
        void testSafeUnbindReference_Exception_ReturnsNull() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(new RuntimeException("Unbind failed"));

            FileReferenceResponse result = fileReferenceCoordinator.safeUnbindReference("job", "entity-123", "file-123");
            assertNull(result);
        }

        @Test
        @DisplayName("safeUnbindReference：404 时返回 null 不抛出")
        void testSafeUnbindReference_404_ReturnsNull() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(404, "Not found")
            );

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
        @DisplayName("safeUnbindAndDeleteIfNeeded：referenceCount=0 时删除文件")
        void testSafeUnbindAndDeleteIfNeeded_RefCountZero_DeletesFile() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse("file-123", "job", "entity-123", 0, true))
            );

            fileReferenceCoordinator.safeUnbindAndDeleteIfNeeded("job", "entity-123", "file-123");

            verify(fileStorageClient).deleteImageFromFileSystem("file-123");
        }

        @Test
        @DisplayName("safeUnbindAndDeleteIfNeeded：referenceCount>0 时不删除文件")
        void testSafeUnbindAndDeleteIfNeeded_RefCountPositive_NotDeletesFile() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse("file-123", "job", "entity-123", 2, true))
            );

            fileReferenceCoordinator.safeUnbindAndDeleteIfNeeded("job", "entity-123", "file-123");

            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }
    }

    @Nested
    @DisplayName("核心流程测试 - executeCreate")
    class ExecuteCreateTests {

        @Test
        @DisplayName("executeCreate：成功流程 - 上传 -> 创建 -> 绑定")
        void testExecuteCreate_Success() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String fileId = "file-123";
            String entityId = "entity-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(fileId));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildBindResponse(fileId, "job", entityId, 1, true))
            );

            FileReferenceCoordinator.EntityCreator<String> creator = fileIdParam -> "created:" + fileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            String result = fileReferenceCoordinator.executeCreate("job", mockFile, creator, idGetter);

            assertEquals("created:file-123", result);
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }

        @Test
        @DisplayName("executeCreate：无文件时跳过上传和绑定")
        void testExecuteCreate_NoFile_SkipsUploadAndBind() {
            String entityId = "entity-123";

            FileReferenceCoordinator.EntityCreator<String> creator = fileIdParam -> "created:" + fileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            String result = fileReferenceCoordinator.executeCreate("job", null, creator, idGetter);

            assertEquals("created:null", result);
            verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
        }

        @Test
        @DisplayName("executeCreate：上传失败时不创建实体")
        void testExecuteCreate_UploadFails_NoEntityCreation() {
            MultipartFile mockFile = mock(MultipartFile.class);
            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenThrow(
                    createFeignException(500, "Upload failed")
            );

            FileReferenceCoordinator.EntityCreator<String> creator = fileIdParam -> "created:" + fileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> "entity-123";

            assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.executeCreate("job", mockFile, creator, idGetter));

            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }

        @Test
        @DisplayName("executeCreate：创建实体失败时删除上传的文件")
        void testExecuteCreate_CreatorFails_DeletesUploadedFile() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String fileId = "file-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(fileId));

            FileReferenceCoordinator.EntityCreator<String> creator = fileIdParam -> {
                throw new RuntimeException("Creator failed");
            };
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> "entity-123";

            assertThrows(RuntimeException.class,
                    () -> fileReferenceCoordinator.executeCreate("job", mockFile, creator, idGetter));

            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(fileId);
        }

        @Test
        @DisplayName("executeCreate：绑定失败时只删除，不调用解绑（bound=false）")
        void testExecuteCreate_BindFails_OnlyDeletesNoUnbind() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String fileId = "file-123";
            String entityId = "entity-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(fileId));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(500, "Bind failed")
            );

            FileReferenceCoordinator.EntityCreator<String> creator = fileIdParam -> "created:" + fileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.executeCreate("job", mockFile, creator, idGetter));

            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(fileId);
        }
    }

    @Nested
    @DisplayName("核心流程测试 - executeUpdate")
    class ExecuteUpdateTests {

        @Test
        @DisplayName("executeUpdate：成功流程 - 上传 -> 更新 -> 绑定新 -> 清理旧")
        void testExecuteUpdate_SuccessWithReplacement() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newFileId = "new-file-123";
            String oldFileId = "old-file-123";
            String entityId = "entity-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newFileId));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildBindResponse(newFileId, "job", entityId, 1, true))
            );
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(oldFileId, "job", entityId, 0, true))
            );
            when(fileStorageClient.deleteImageFromFileSystem(oldFileId)).thenReturn(ResponseEntity.ok().build());

            FileReferenceCoordinator.EntityUpdater<String> updater = newFileIdParam -> "updated:" + newFileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            String result = fileReferenceCoordinator.executeUpdate("job", oldFileId, mockFile, updater, idGetter);

            assertEquals("updated:new-file-123", result);
            verify(fileStorageClient).uploadImageToFIleSystem(mockFile);
            verify(fileStorageClient).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(oldFileId);
        }

        @Test
        @DisplayName("executeUpdate：无新文件时跳过上传，不清理旧引用")
        void testExecuteUpdate_NoNewFile_SkipsUpload() {
            String oldFileId = "old-file-123";
            String entityId = "entity-123";

            FileReferenceCoordinator.EntityUpdater<String> updater = newFileIdParam -> "updated:" + newFileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            String result = fileReferenceCoordinator.executeUpdate("job", oldFileId, null, updater, idGetter);

            assertEquals("updated:null", result);
            verify(fileStorageClient, never()).uploadImageToFIleSystem(any(MultipartFile.class));
            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }

        @Test
        @DisplayName("executeUpdate：上传失败时不更新实体")
        void testExecuteUpdate_UploadFails_NoUpdate() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String oldFileId = "old-file-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenThrow(
                    createFeignException(500, "Upload failed")
            );

            FileReferenceCoordinator.EntityUpdater<String> updater = newFileIdParam -> "updated:" + newFileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> "entity-123";

            assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.executeUpdate("job", oldFileId, mockFile, updater, idGetter));

            verify(fileStorageClient, never()).bindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(oldFileId);
        }

        @Test
        @DisplayName("executeUpdate：绑定新引用失败时只回滚新引用")
        void testExecuteUpdate_BindFails_RollbacksNewOnly() {
            MultipartFile mockFile = mock(MultipartFile.class);
            String newFileId = "new-file-123";
            String oldFileId = "old-file-123";
            String entityId = "entity-123";

            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenReturn(ResponseEntity.ok(newFileId));
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(500, "Bind failed")
            );
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenReturn(
                    ResponseEntity.ok(buildUnbindResponse(newFileId, "job", entityId, 0, true))
            );

            FileReferenceCoordinator.EntityUpdater<String> updater = newFileIdParam -> "updated:" + newFileIdParam;
            FileReferenceCoordinator.EntityIdGetter<String> idGetter = entity -> entityId;

            assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.executeUpdate("job", oldFileId, mockFile, updater, idGetter));

            verify(fileStorageClient).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient).deleteImageFromFileSystem(newFileId);
            verify(fileStorageClient, never()).deleteImageFromFileSystem(oldFileId);
        }
    }

    @Nested
    @DisplayName("核心流程测试 - executeDelete")
    class ExecuteDeleteTests {

        @Test
        @DisplayName("executeDelete：先执行删除动作，再清理文件引用")
        void testExecuteDelete_OrderOfOperations() {
            String fileId = "file-123";
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

        @Test
        @DisplayName("executeDelete：无文件时只执行删除动作")
        void testExecuteDelete_NoFile_OnlyDeletionAction() {
            String entityId = "entity-123";
            Runnable deletionAction = mock(Runnable.class);

            fileReferenceCoordinator.executeDelete("job", entityId, null, deletionAction);

            verify(deletionAction).run();
            verify(fileStorageClient, never()).unbindReference(any(FileReferenceRequest.class));
            verify(fileStorageClient, never()).deleteImageFromFileSystem(anyString());
        }
    }

    @Nested
    @DisplayName("错误码处理测试")
    class ErrorCodeHandlingTests {

        @Test
        @DisplayName("bindReference 404：抛出 FeignException.NotFound")
        void testBindReference_404_ThrowsNotFound() {
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(404, "File not found")
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.bindReference("job", "entity-123", "file-123"));

            assertEquals(404, exception.status());
        }

        @Test
        @DisplayName("bindReference 409：抛出 FeignException.Conflict")
        void testBindReference_409_ThrowsConflict() {
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(409, "Conflict")
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.bindReference("job", "entity-123", "file-123"));

            assertEquals(409, exception.status());
        }

        @Test
        @DisplayName("bindReference 500：抛出 FeignException，status=500")
        void testBindReference_500_ThrowsServerError() {
            when(fileStorageClient.bindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(500, "Internal server error")
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> fileReferenceCoordinator.bindReference("job", "entity-123", "file-123"));

            assertEquals(500, exception.status());
        }

        @Test
        @DisplayName("uploadImage 5xx：抛出 FeignException，status=5xx")
        void testUploadImage_5xx_Throws() {
            MultipartFile mockFile = mock(MultipartFile.class);
            when(fileStorageClient.uploadImageToFIleSystem(mockFile)).thenThrow(
                    createFeignException(503, "Service unavailable")
            );

            FeignException exception = assertThrows(FeignException.class,
                    () -> fileStorageClientAdapter.uploadImage(mockFile));

            assertEquals(503, exception.status());
        }

        @Test
        @DisplayName("unbindReference 404：safeUnbindReference 返回 null")
        void testSafeUnbindReference_404_ReturnsNull() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(404, "Reference not found")
            );

            FileReferenceResponse result = fileReferenceCoordinator.safeUnbindReference("job", "entity-123", "file-123");
            assertNull(result);
        }

        @Test
        @DisplayName("unbindReference 5xx：safeUnbindReference 返回 null")
        void testSafeUnbindReference_5xx_ReturnsNull() {
            when(fileStorageClient.unbindReference(any(FileReferenceRequest.class))).thenThrow(
                    createFeignException(503, "Service unavailable")
            );

            FileReferenceResponse result = fileReferenceCoordinator.safeUnbindReference("job", "entity-123", "file-123");
            assertNull(result);
        }
    }
}
