package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.dto.FileReferenceRequest;
import com.safalifter.jobservice.dto.FileReferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileReferenceCoordinator {
    private final FileStorageClient fileStorageClient;

    public FileReferenceResponse bindReference(String entityType, String entityId, String fileId) {
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return null;
        }
        try {
            var response = fileStorageClient.bindReference(buildRequest(fileId, entityType, entityId));
            log.info("Bound file {} to {}:{}", fileId, entityType, entityId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to bind file {} to {}:{}", fileId, entityType, entityId, e);
            throw e;
        }
    }

    public FileReferenceResponse safeUnbindReference(String entityType, String entityId, String fileId) {
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return null;
        }
        try {
            var response = fileStorageClient.unbindReference(buildRequest(fileId, entityType, entityId));
            log.info("Unbound file {} from {}:{}", fileId, entityType, entityId);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to unbind file {} from {}:{}", fileId, entityType, entityId, e);
            return null;
        }
    }

    public void safeUnbindAndDeleteIfNeeded(String entityType, String entityId, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        FileReferenceResponse response = safeUnbindReference(entityType, entityId, fileId);
        if (response != null && response.isUnbound()) {
            Integer count = response.getReferenceCount();
            if (count != null && count == 0) {
                safeDeleteFile(fileId);
            }
        }
    }

    public void safeDeleteFile(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        try {
            fileStorageClient.deleteImageFromFileSystem(fileId);
            log.info("Deleted file: {}", fileId);
        } catch (Exception e) {
            log.warn("Failed to delete file: {}", fileId, e);
        }
    }

    private String doUpload(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            var response = fileStorageClient.uploadImageToFIleSystem(file);
            String fileId = response.getBody();
            log.info("Uploaded file: {}", fileId);
            return fileId;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw e;
        }
    }

    private FileReferenceRequest buildRequest(String fileId, String entityType, String entityId) {
        return FileReferenceRequest.builder()
                .fileId(fileId)
                .entityType(entityType)
                .entityId(entityId)
                .build();
    }

    private RuntimeException wrapIfNeeded(Exception e) {
        return e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    }

    @FunctionalInterface
    public interface EntityCreator<T> {
        T createEntity(String fileId) throws Exception;
    }

    @FunctionalInterface
    public interface EntityUpdater<T> {
        T updateEntity(String newFileId) throws Exception;
    }

    @FunctionalInterface
    public interface EntityIdGetter<T> {
        String getEntityId(T entity);
    }

    public <T> T executeCreate(
            String entityType,
            MultipartFile file,
            EntityCreator<T> creator,
            EntityIdGetter<T> idGetter
    ) {
        String fileId = null;
        T savedEntity = null;
        String entityId = null;
        boolean bound = false;

        try {
            fileId = doUpload(file);
            savedEntity = creator.createEntity(fileId);
            entityId = idGetter.getEntityId(savedEntity);

            if (fileId != null && entityId != null) {
                bindReference(entityType, entityId, fileId);
                bound = true;
            }
            return savedEntity;

        } catch (Exception e) {
            if (bound && entityId != null && fileId != null) {
                safeUnbindReference(entityType, entityId, fileId);
            }
            if (fileId != null) {
                safeDeleteFile(fileId);
            }
            throw wrapIfNeeded(e);
        }
    }

    public <T> T executeUpdate(
            String entityType,
            String oldFileId,
            MultipartFile file,
            EntityUpdater<T> updater,
            EntityIdGetter<T> idGetter
    ) {
        String newFileId = null;
        T savedEntity = null;
        String entityId = null;
        boolean newBound = false;

        try {
            newFileId = doUpload(file);
            savedEntity = updater.updateEntity(newFileId);
            entityId = idGetter.getEntityId(savedEntity);

            if (newFileId != null && entityId != null) {
                bindReference(entityType, entityId, newFileId);
                newBound = true;
            }

            if (shouldCleanupOld(oldFileId, newFileId)) {
                safeUnbindAndDeleteIfNeeded(entityType, entityId, oldFileId);
            }
            return savedEntity;

        } catch (Exception e) {
            if (newBound && entityId != null && newFileId != null) {
                safeUnbindReference(entityType, entityId, newFileId);
            }
            if (newFileId != null) {
                safeDeleteFile(newFileId);
            }
            throw wrapIfNeeded(e);
        }
    }

    public void executeDelete(
            String entityType,
            String entityId,
            String fileId,
            Runnable deletionAction
    ) {
        deletionAction.run();
        safeUnbindAndDeleteIfNeeded(entityType, entityId, fileId);
    }

    private boolean shouldCleanupOld(String oldFileId, String newFileId) {
        return StringUtils.hasText(oldFileId)
                && StringUtils.hasText(newFileId)
                && !oldFileId.equals(newFileId);
    }
}
