package com.safalifter.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@FeignClient(name = "file-storage", path = "/v1/file-storage")
public interface FileStorageClient {
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<String> uploadImageToFIleSystem(@RequestPart("image") MultipartFile file);

    @DeleteMapping("/delete/{id}")
    ResponseEntity<Void> deleteImageFromFileSystem(@PathVariable String id);

    @PostMapping("/bind-reference")
    ResponseEntity<Map<String, Object>> bindReference(@RequestBody Map<String, String> request);

    @PostMapping("/unbind-reference")
    ResponseEntity<Map<String, Object>> unbindReference(@RequestBody Map<String, String> request);

    @DeleteMapping("/unbind-all/{entityType}/{entityId}")
    ResponseEntity<Map<String, String>> unbindAllReferences(
            @PathVariable String entityType,
            @PathVariable String entityId);

    @GetMapping("/reference-count/{fileId}")
    ResponseEntity<Map<String, Integer>> getReferenceCount(@PathVariable String fileId);
}
