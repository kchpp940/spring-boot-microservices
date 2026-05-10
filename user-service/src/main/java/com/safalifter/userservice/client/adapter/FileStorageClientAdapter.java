package com.safalifter.userservice.client.adapter;

import com.safalifter.userservice.client.FileStorageClient;
import com.safalifter.userservice.dto.FileReferenceRequest;
import com.safalifter.userservice.dto.FileReferenceResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageClientAdapter {

    private final FileStorageClient fileStorageClient;

    public String uploadImage(MultipartFile file) {
        ResponseEntity<String> response = fileStorageClient.uploadImageToFIleSystem(file);
        return extractBodyOrThrow(response, "Failed to upload image");
    }

    public void deleteImage(String id) {
        fileStorageClient.deleteImageFromFileSystem(id);
    }

    public FileReferenceResponse bindReference(FileReferenceRequest request) {
        try {
            ResponseEntity<FileReferenceResponse> response = fileStorageClient.bindReference(request);
            return extractBodyOrThrow(response, "Failed to bind reference");
        } catch (FeignException e) {
            log.error("Failed to bind reference for fileId={}, entityType={}, entityId={} (status={})",
                    request.getFileId(), request.getEntityType(), request.getEntityId(), e.status(), e);
            throw e;
        }
    }

    public FileReferenceResponse unbindReference(FileReferenceRequest request) {
        try {
            ResponseEntity<FileReferenceResponse> response = fileStorageClient.unbindReference(request);
            return extractBodyOrThrow(response, "Failed to unbind reference");
        } catch (FeignException e) {
            log.error("Failed to unbind reference for fileId={}, entityType={}, entityId={} (status={})",
                    request.getFileId(), request.getEntityType(), request.getEntityId(), e.status(), e);
            throw e;
        }
    }

    public void unbindAllReferences(String entityType, String entityId) {
        fileStorageClient.unbindAllReferences(entityType, entityId);
    }

    public Integer getReferenceCount(String fileId) {
        try {
            ResponseEntity<Map<String, Integer>> response = fileStorageClient.getReferenceCount(fileId);
            Map<String, Integer> body = extractBodyOrNull(response);
            if (body != null && body.containsKey("referenceCount")) {
                return body.get("referenceCount");
            }
            return null;
        } catch (FeignException.NotFound e) {
            log.warn("Reference count not found for fileId={}", fileId);
            return null;
        } catch (FeignException e) {
            log.error("Failed to get reference count for fileId={} (status={})", fileId, e.status(), e);
            return null;
        }
    }

    private <T> T extractBodyOrThrow(ResponseEntity<T> response, String errorMessage) {
        if (response == null) {
            throw new IllegalStateException(errorMessage + ": empty response");
        }
        T body = response.getBody();
        if (body == null) {
            throw new IllegalStateException(errorMessage + ": empty body");
        }
        return body;
    }

    private <T> T extractBodyOrNull(ResponseEntity<T> response) {
        return response != null ? response.getBody() : null;
    }
}
