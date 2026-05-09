package com.safalifter.filestorage.controller;

import com.safalifter.filestorage.model.File;
import com.safalifter.filestorage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageController storageController;

    private File testFilePng;
    private File testFileJpeg;
    private File testFileUnknownType;
    private byte[] testContent;

    @BeforeEach
    void setUp() {
        testContent = "test image content".getBytes();

        testFilePng = File.builder()
                .id("png-file-id")
                .type(MediaType.IMAGE_PNG_VALUE)
                .filePath("/tmp/storage/png-file-id")
                .build();

        testFileJpeg = File.builder()
                .id("jpeg-file-id")
                .type(MediaType.IMAGE_JPEG_VALUE)
                .filePath("/tmp/storage/jpeg-file-id")
                .build();

        testFileUnknownType = File.builder()
                .id("unknown-file-id")
                .type(null)
                .filePath("/tmp/storage/unknown-file-id")
                .build();
    }

    @Test
    @DisplayName("uploadImageToFIleSystem should return file id")
    void testUploadImage_ReturnsFileId() {
        MultipartFile mockFile = mock(MultipartFile.class);
        String expectedFileId = "new-file-id-123";

        when(storageService.uploadImageToFileSystem(mockFile)).thenReturn(expectedFileId);

        ResponseEntity<String> response = storageController.uploadImageToFIleSystem(mockFile);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedFileId, response.getBody());
        verify(storageService).uploadImageToFileSystem(mockFile);
    }

    @Test
    @DisplayName("downloadImageFromFileSystem should return PNG content-type for PNG file")
    void testDownload_ReturnsPngContentType_ForPngFile() {
        when(storageService.getFileMetadata("png-file-id")).thenReturn(testFilePng);
        when(storageService.downloadImageFromFileSystem("png-file-id")).thenReturn(testContent);

        ResponseEntity<?> response = storageController.downloadImageFromFileSystem("png-file-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertArrayEquals(testContent, (byte[]) response.getBody());
    }

    @Test
    @DisplayName("downloadImageFromFileSystem should return JPEG content-type for JPEG file")
    void testDownload_ReturnsJpegContentType_ForJpegFile() {
        when(storageService.getFileMetadata("jpeg-file-id")).thenReturn(testFileJpeg);
        when(storageService.downloadImageFromFileSystem("jpeg-file-id")).thenReturn(testContent);

        ResponseEntity<?> response = storageController.downloadImageFromFileSystem("jpeg-file-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertArrayEquals(testContent, (byte[]) response.getBody());
    }

    @Test
    @DisplayName("downloadImageFromFileSystem should return APPLICATION_OCTET_STREAM for unknown content-type")
    void testDownload_ReturnsOctetStream_ForUnknownContentType() {
        when(storageService.getFileMetadata("unknown-file-id")).thenReturn(testFileUnknownType);
        when(storageService.downloadImageFromFileSystem("unknown-file-id")).thenReturn(testContent);

        ResponseEntity<?> response = storageController.downloadImageFromFileSystem("unknown-file-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
        assertArrayEquals(testContent, (byte[]) response.getBody());
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should return OK")
    void testDelete_ReturnsOk() {
        doNothing().when(storageService).deleteImageFromFileSystem("file-to-delete");

        ResponseEntity<Void> response = storageController.deleteImageFromFileSystem("file-to-delete");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(storageService).deleteImageFromFileSystem("file-to-delete");
    }
}
