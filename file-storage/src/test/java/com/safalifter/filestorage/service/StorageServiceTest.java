package com.safalifter.filestorage.service;

import com.safalifter.filestorage.exc.GenericErrorResponse;
import com.safalifter.filestorage.model.File;
import com.safalifter.filestorage.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private StorageService storageService;

    @TempDir
    Path tempDir;

    private File testFile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "storagePath", tempDir.toString());
        storageService.init();

        testFile = File.builder()
                .id("test-file-id-123")
                .type(MediaType.IMAGE_JPEG_VALUE)
                .filePath(tempDir.resolve("test-file-id-123").toString())
                .build();
    }

    @Test
    @DisplayName("uploadImageToFileSystem should use configurable storage path and save original contentType")
    void testUploadImageToFileSystem_UsesConfiguredPathAndSavesContentType() throws IOException {
        MultipartFile mockMultipartFile = mock(MultipartFile.class);
        String expectedContentType = MediaType.IMAGE_PNG_VALUE;

        when(mockMultipartFile.getContentType()).thenReturn(expectedContentType);
        doNothing().when(mockMultipartFile).transferTo(any(java.io.File.class));
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String fileId = storageService.uploadImageToFileSystem(mockMultipartFile);

        assertNotNull(fileId);
        verify(fileRepository).save(argThat(savedFile -> {
            assertEquals(expectedContentType, savedFile.getType());
            assertTrue(savedFile.getFilePath().startsWith(tempDir.toString()));
            return true;
        }));
        verify(mockMultipartFile).transferTo(any(java.io.File.class));
    }

    @Test
    @DisplayName("uploadImageToFileSystem should throw GenericErrorResponse when transfer fails")
    void testUploadImageToFileSystem_ThrowsException_WhenTransferFails() throws IOException {
        MultipartFile mockMultipartFile = mock(MultipartFile.class);
        when(mockMultipartFile.getContentType()).thenReturn(MediaType.IMAGE_JPEG_VALUE);
        doThrow(new IOException("Disk full")).when(mockMultipartFile).transferTo(any(java.io.File.class));

        GenericErrorResponse exception = assertThrows(GenericErrorResponse.class,
                () -> storageService.uploadImageToFileSystem(mockMultipartFile));

        assertEquals("Unable to save file to storage", exception.getMessage());
        verify(fileRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("getFileMetadata should return file metadata with correct contentType")
    void testGetFileMetadata_ReturnsMetadataWithContentType() {
        when(fileRepository.findById("test-file-id-123")).thenReturn(Optional.of(testFile));

        File result = storageService.getFileMetadata("test-file-id-123");

        assertNotNull(result);
        assertEquals("test-file-id-123", result.getId());
        assertEquals(MediaType.IMAGE_JPEG_VALUE, result.getType());
        assertEquals(tempDir.resolve("test-file-id-123").toString(), result.getFilePath());
    }

    @Test
    @DisplayName("getFileMetadata should throw GenericErrorResponse when file not found")
    void testGetFileMetadata_ThrowsException_WhenFileNotFound() {
        when(fileRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        GenericErrorResponse exception = assertThrows(GenericErrorResponse.class,
                () -> storageService.getFileMetadata("non-existent-id"));

        assertEquals("File not found", exception.getMessage());
    }

    @Test
    @DisplayName("downloadImageFromFileSystem should read file content from stored path")
    void testDownloadImageFromFileSystem_ReadsContentFromPath() throws IOException {
        Path testFilePath = tempDir.resolve("test-file-id-123");
        byte[] expectedContent = "test file content".getBytes();
        Files.write(testFilePath, expectedContent);

        when(fileRepository.findById("test-file-id-123")).thenReturn(Optional.of(testFile));

        byte[] result = storageService.downloadImageFromFileSystem("test-file-id-123");

        assertArrayEquals(expectedContent, result);
    }

    @Test
    @DisplayName("downloadImageFromFileSystem should throw GenericErrorResponse when read fails")
    void testDownloadImageFromFileSystem_ThrowsException_WhenReadFails() {
        File nonExistentFile = File.builder()
                .id("non-existent-file")
                .type(MediaType.IMAGE_JPEG_VALUE)
                .filePath(tempDir.resolve("non-existent-file").toString())
                .build();

        when(fileRepository.findById("non-existent-file")).thenReturn(Optional.of(nonExistentFile));

        GenericErrorResponse exception = assertThrows(GenericErrorResponse.class,
                () -> storageService.downloadImageFromFileSystem("non-existent-file"));

        assertEquals("Unable to read file from storage", exception.getMessage());
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should not throw when id is empty")
    void testDeleteImageFromFileSystem_DoesNotThrow_WhenIdIsEmpty() {
        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem(""));
        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem("   "));
        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem(null));

        verify(fileRepository, never()).findById(anyString());
        verify(fileRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should not throw when record does not exist")
    void testDeleteImageFromFileSystem_DoesNotThrow_WhenRecordDoesNotExist() {
        when(fileRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem("non-existent-id"));

        verify(fileRepository).findById("non-existent-id");
        verify(fileRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should delete record even when physical file does not exist")
    void testDeleteImageFromFileSystem_DeletesRecord_WhenPhysicalFileDoesNotExist() {
        when(fileRepository.findById("test-file-id-123")).thenReturn(Optional.of(testFile));

        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem("test-file-id-123"));

        verify(fileRepository).findById("test-file-id-123");
        verify(fileRepository).deleteById("test-file-id-123");
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should delete physical file and record when both exist")
    void testDeleteImageFromFileSystem_DeletesFileAndRecord_WhenBothExist() throws IOException {
        Path testFilePath = tempDir.resolve("test-file-id-123");
        Files.write(testFilePath, "content".getBytes());
        assertTrue(Files.exists(testFilePath));

        when(fileRepository.findById("test-file-id-123")).thenReturn(Optional.of(testFile));

        assertDoesNotThrow(() -> storageService.deleteImageFromFileSystem("test-file-id-123"));

        assertFalse(Files.exists(testFilePath));
        verify(fileRepository).deleteById("test-file-id-123");
    }

    @Test
    @DisplayName("deleteImageFromFileSystem should throw GenericErrorResponse when physical file deletion fails")
    void testDeleteImageFromFileSystem_ThrowsException_WhenPhysicalFileDeletionFails() throws IOException {
        Path testFilePath = tempDir.resolve("test-file-id-123");
        Files.write(testFilePath, "content".getBytes());

        java.io.File mockFile = mock(java.io.File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.delete()).thenReturn(false);

        StorageService spyService = spy(storageService);
        doReturn(mockFile).when(spyService).findFileById("test-file-id-123");
        doReturn(Optional.of(testFile)).when(fileRepository).findById("test-file-id-123");

        GenericErrorResponse exception = assertThrows(GenericErrorResponse.class,
                () -> spyService.deleteImageFromFileSystem("test-file-id-123"));

        assertEquals("Unable to delete file from storage", exception.getMessage());
    }
}
