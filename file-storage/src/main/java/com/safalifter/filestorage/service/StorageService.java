package com.safalifter.filestorage.service;

import com.safalifter.filestorage.dto.FileReferenceRequest;
import com.safalifter.filestorage.dto.FileReferenceResponse;
import com.safalifter.filestorage.exc.GenericErrorResponse;
import com.safalifter.filestorage.model.File;
import com.safalifter.filestorage.model.FileReference;
import com.safalifter.filestorage.repository.FileReferenceRepository;
import com.safalifter.filestorage.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {
    private final FileRepository fileRepository;
    private final FileReferenceRepository fileReferenceRepository;

    @Value("${file.storage.path}")
    private String storagePath;

    private java.io.File storageFolder;

    @PostConstruct
    public void init() {
        storageFolder = new java.io.File(storagePath);
        if (!storageFolder.exists()) {
            boolean directoriesCreated = storageFolder.mkdirs();
            if (!directoriesCreated) {
                throw GenericErrorResponse.builder()
                        .message("Unable to create storage directories at: " + storagePath)
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build();
            }
        }
    }

    public String uploadImageToFileSystem(MultipartFile file) {
        String uuid = UUID.randomUUID().toString();
        java.io.File targetFile = new java.io.File(storageFolder, uuid);

        try {
            file.transferTo(targetFile);
        } catch (IOException e) {
            log.error("Failed to save file to storage", e);
            throw GenericErrorResponse.builder()
                    .message("Unable to save file to storage")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        fileRepository.save(File.builder()
                .id(uuid)
                .type(file.getContentType())
                .filePath(targetFile.getAbsolutePath())
                .referenceCount(0)
                .build());
        return uuid;
    }

    public File getFileMetadata(String id) {
        return findFileById(id);
    }

    public byte[] downloadImageFromFileSystem(String id) {
        java.io.File file = new java.io.File(findFileById(id).getFilePath());
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            log.error("Failed to read file from storage", e);
            throw GenericErrorResponse.builder()
                    .message("Unable to read file from storage")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    @Transactional
    public void deleteImageFromFileSystem(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }

        File fileMetadata = fileRepository.findById(id).orElse(null);
        if (fileMetadata == null) {
            log.warn("File not found for deletion: {}", id);
            return;
        }

        long referenceCount = fileReferenceRepository.countByFileId(id);
        if (referenceCount > 0) {
            log.warn("Cannot delete file {} as it still has {} references", id, referenceCount);
            throw GenericErrorResponse.builder()
                    .message("Cannot delete file: it is still referenced by " + referenceCount + " entities")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        java.io.File file = new java.io.File(fileMetadata.getFilePath());

        if (file.exists()) {
            boolean deletionResult = file.delete();
            if (!deletionResult) {
                log.error("Failed to delete file from storage: {}", fileMetadata.getFilePath());
                throw GenericErrorResponse.builder()
                        .message("Unable to delete file from storage")
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build();
            }
        }

        fileRepository.deleteById(id);
        log.info("Successfully deleted file: {}", id);
    }

    @Transactional
    public FileReferenceResponse bindReference(FileReferenceRequest request) {
        validateRequest(request);

        File file = findFileById(request.getFileId());
        
        boolean alreadyBound = fileReferenceRepository
                .findByFileIdAndEntityTypeAndEntityId(request.getFileId(), request.getEntityType(), request.getEntityId())
                .isPresent();

        if (alreadyBound) {
            log.warn("Duplicate bind attempt: file {} already bound to entity {}:{}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId());
            return FileReferenceResponse.builder()
                    .fileId(request.getFileId())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .referenceCount(file.getReferenceCount())
                    .bound(false)
                    .build();
        }

        try {
            FileReference reference = FileReference.builder()
                    .fileId(request.getFileId())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .build();
            fileReferenceRepository.save(reference);

            file.setReferenceCount(file.getReferenceCount() + 1);
            fileRepository.save(file);

            log.info("Successfully bound file {} to entity {}:{}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId());

            return FileReferenceResponse.builder()
                    .fileId(request.getFileId())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .referenceCount(file.getReferenceCount())
                    .bound(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to bind reference for file {} to entity {}:{}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId(), e);
            throw GenericErrorResponse.builder()
                    .message("Failed to bind file reference")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    @Transactional
    public FileReferenceResponse unbindReference(FileReferenceRequest request) {
        validateRequest(request);

        File file = findFileById(request.getFileId());

        boolean bound = fileReferenceRepository
                .findByFileIdAndEntityTypeAndEntityId(request.getFileId(), request.getEntityType(), request.getEntityId())
                .isPresent();

        if (!bound) {
            log.warn("Unbind attempt for non-existent reference: file {} not bound to entity {}:{}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId());
            return FileReferenceResponse.builder()
                    .fileId(request.getFileId())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .referenceCount(file.getReferenceCount())
                    .unbound(false)
                    .build();
        }

        try {
            fileReferenceRepository.deleteByFileIdAndEntityTypeAndEntityId(
                    request.getFileId(), request.getEntityType(), request.getEntityId());

            file.setReferenceCount(Math.max(0, file.getReferenceCount() - 1));
            fileRepository.save(file);

            log.info("Successfully unbound file {} from entity {}:{}. New reference count: {}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId(), file.getReferenceCount());

            if (file.getReferenceCount() == 0) {
                log.info("File {} has no more references, can be safely deleted", request.getFileId());
            }

            return FileReferenceResponse.builder()
                    .fileId(request.getFileId())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .referenceCount(file.getReferenceCount())
                    .unbound(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to unbind reference for file {} from entity {}:{}", 
                    request.getFileId(), request.getEntityType(), request.getEntityId(), e);
            throw GenericErrorResponse.builder()
                    .message("Failed to unbind file reference")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    @Transactional
    public void unbindAllReferencesForEntity(String entityType, String entityId) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return;
        }

        var references = fileReferenceRepository.findByEntityTypeAndEntityId(entityType, entityId);
        
        for (FileReference reference : references) {
            File file = fileRepository.findById(reference.getFileId()).orElse(null);
            if (file != null) {
                file.setReferenceCount(Math.max(0, file.getReferenceCount() - 1));
                fileRepository.save(file);
            }
        }
        
        fileReferenceRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
        log.info("Unbound all references for entity {}:{}", entityType, entityId);
    }

    public int getReferenceCount(String fileId) {
        File file = findFileById(fileId);
        return file.getReferenceCount();
    }

    private void validateRequest(FileReferenceRequest request) {
        if (!StringUtils.hasText(request.getFileId())) {
            throw GenericErrorResponse.builder()
                    .message("File ID is required")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!StringUtils.hasText(request.getEntityType())) {
            throw GenericErrorResponse.builder()
                    .message("Entity type is required")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            throw GenericErrorResponse.builder()
                    .message("Entity ID is required")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    protected File findFileById(String id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("File not found: {}", id);
                    return GenericErrorResponse.builder()
                            .message("File not found: " + id)
                            .httpStatus(HttpStatus.NOT_FOUND)
                            .build();
                });
    }
}
