package com.safalifter.filestorage.controller;

import com.safalifter.filestorage.dto.FileReferenceRequest;
import com.safalifter.filestorage.dto.FileReferenceResponse;
import com.safalifter.filestorage.model.File;
import com.safalifter.filestorage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/file-storage")
@RequiredArgsConstructor
public class StorageController {
    private final StorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadImageToFIleSystem(@RequestPart("image") MultipartFile file) {
        return ResponseEntity.ok().body(storageService.uploadImageToFileSystem(file));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadImageFromFileSystem(@PathVariable String id) {
        File fileMetadata = storageService.getFileMetadata(id);
        MediaType mediaType = determineMediaType(fileMetadata.getType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(storageService.downloadImageFromFileSystem(id));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteImageFromFileSystem(@PathVariable String id) {
        storageService.deleteImageFromFileSystem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bind-reference")
    public ResponseEntity<FileReferenceResponse> bindReference(@RequestBody FileReferenceRequest request) {
        return ResponseEntity.ok(storageService.bindReference(request));
    }

    @PostMapping("/unbind-reference")
    public ResponseEntity<FileReferenceResponse> unbindReference(@RequestBody FileReferenceRequest request) {
        return ResponseEntity.ok(storageService.unbindReference(request));
    }

    @DeleteMapping("/unbind-all/{entityType}/{entityId}")
    public ResponseEntity<Map<String, String>> unbindAllReferences(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        storageService.unbindAllReferencesForEntity(entityType, entityId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Successfully unbound all references for entity " + entityType + ":" + entityId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reference-count/{fileId}")
    public ResponseEntity<Map<String, Integer>> getReferenceCount(@PathVariable String fileId) {
        int count = storageService.getReferenceCount(fileId);
        Map<String, Integer> response = new HashMap<>();
        response.put("referenceCount", count);
        return ResponseEntity.ok(response);
    }

    private MediaType determineMediaType(String contentType) {
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
