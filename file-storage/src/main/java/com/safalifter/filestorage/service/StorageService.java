package com.safalifter.filestorage.service;

import com.safalifter.filestorage.exc.GenericErrorResponse;
import com.safalifter.filestorage.model.File;
import com.safalifter.filestorage.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final FileRepository fileRepository;

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
            throw GenericErrorResponse.builder()
                    .message("Unable to save file to storage")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        fileRepository.save(File.builder()
                .id(uuid)
                .type(file.getContentType())
                .filePath(targetFile.getAbsolutePath())
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
            throw GenericErrorResponse.builder()
                    .message("Unable to read file from storage")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    public void deleteImageFromFileSystem(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }

        File fileMetadata = fileRepository.findById(id).orElse(null);
        if (fileMetadata == null) {
            return;
        }

        java.io.File file = new java.io.File(fileMetadata.getFilePath());

        if (file.exists()) {
            boolean deletionResult = file.delete();
            if (!deletionResult) {
                throw GenericErrorResponse.builder()
                        .message("Unable to delete file from storage")
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build();
            }
        }

        fileRepository.deleteById(id);
    }

    protected File findFileById(String id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> GenericErrorResponse.builder()
                        .message("File not found")
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());
    }
}