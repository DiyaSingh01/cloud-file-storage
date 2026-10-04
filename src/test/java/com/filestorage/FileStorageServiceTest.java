package com.filestorage;

import com.filestorage.dto.FileUploadResponse;
import com.filestorage.dto.PresignedUrlResponse;
import com.filestorage.dto.StorageStatsResponse;
import com.filestorage.exception.FileNotFoundException;
import com.filestorage.exception.InvalidFileException;
import com.filestorage.model.FileMetadata;
import com.filestorage.model.FileStatus;
import com.filestorage.repository.FileMetadataRepository;
import com.filestorage.service.FileStorageService;
import com.filestorage.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(s3Service, fileMetadataRepository);
        ReflectionTestUtils.setField(fileStorageService, "allowedMimeTypes", "image/jpeg,image/png,application/pdf,text/plain");
        ReflectionTestUtils.setField(fileStorageService, "maxFileSizeBytes", 52428800L);
        ReflectionTestUtils.setField(fileStorageService, "awsRegion", "ap-south-1");
    }

    @Test
    @DisplayName("Should upload file successfully and save metadata")
    void uploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-image.jpg", "image/jpeg", "fake-image-content".getBytes());

        FileMetadata savedMetadata = FileMetadata.builder()
                .id(1L)
                .originalFileName("test-image.jpg")
                .s3Key("testuser/12345_test-image.jpg")
                .bucketName("test-bucket")
                .contentType("image/jpeg")
                .fileSizeBytes((long) "fake-image-content".getBytes().length)
                .owner("testuser")
                .status(FileStatus.ACTIVE)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(s3Service.getBucketName()).thenReturn("test-bucket");
        doNothing().when(s3Service).uploadFile(anyString(), any(), anyString(), anyLong());
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenReturn(savedMetadata);

        FileUploadResponse response = fileStorageService.uploadFile(file, "testuser", "Test file", "test,image");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test-image.jpg", response.getOriginalFileName());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals(FileStatus.ACTIVE, response.getStatus());

        verify(s3Service).uploadFile(anyString(), any(), eq("image/jpeg"), anyLong());
        verify(fileMetadataRepository).save(any(FileMetadata.class));
    }

    @Test
    @DisplayName("Should reject empty file")
    void uploadFile_EmptyFile_ThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.uploadFile(emptyFile, "testuser", null, null));

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("Should reject disallowed MIME type")
    void uploadFile_DisallowedMimeType_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", "binary-content".getBytes());

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.uploadFile(file, "testuser", null, null));

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("Should reject file exceeding max size")
    void uploadFile_ExceedsMaxSize_ThrowsException() {
        ReflectionTestUtils.setField(fileStorageService, "maxFileSizeBytes", 10L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", "content-longer-than-10-bytes".getBytes());

        assertThrows(InvalidFileException.class,
                () -> fileStorageService.uploadFile(file, "testuser", null, null));
    }

    @Test
    @DisplayName("Should generate presigned URL for active file")
    void generatePresignedUrl_Success() {
        FileMetadata metadata = buildActiveMetadata(1L, "testuser");

        when(fileMetadataRepository.findByIdAndStatus(1L, FileStatus.ACTIVE))
                .thenReturn(Optional.of(metadata));
        when(s3Service.generatePresignedUrl(metadata.getS3Key()))
                .thenReturn("https://s3.amazonaws.com/bucket/key?signature=abc");
        when(s3Service.getPresignedUrlExpiryMinutes()).thenReturn(60);

        PresignedUrlResponse response = fileStorageService.generatePresignedUrl(1L, "testuser");

        assertNotNull(response);
        assertEquals(1L, response.getFileId());
        assertTrue(response.getPresignedUrl().startsWith("https://"));
        assertEquals(60, response.getExpiryMinutes());
    }

    @Test
    @DisplayName("Should throw exception when generating presigned URL for non-existent file")
    void generatePresignedUrl_FileNotFound_ThrowsException() {
        when(fileMetadataRepository.findByIdAndStatus(999L, FileStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(FileNotFoundException.class,
                () -> fileStorageService.generatePresignedUrl(999L, "testuser"));
    }

    @Test
    @DisplayName("Should soft delete file and remove from S3")
    void deleteFile_Success() {
        FileMetadata metadata = buildActiveMetadata(1L, "testuser");

        when(fileMetadataRepository.findByIdAndStatus(1L, FileStatus.ACTIVE))
                .thenReturn(Optional.of(metadata));
        doNothing().when(s3Service).deleteFile(metadata.getS3Key());
        when(fileMetadataRepository.save(any())).thenReturn(metadata);

        assertDoesNotThrow(() -> fileStorageService.deleteFile(1L, "testuser"));

        verify(s3Service).deleteFile(metadata.getS3Key());
        verify(fileMetadataRepository).save(argThat(m -> m.getStatus() == FileStatus.DELETED));
    }

    @Test
    @DisplayName("Should return storage stats for owner")
    void getStorageStats_Success() {
        when(fileMetadataRepository.countActiveFilesByOwner("testuser")).thenReturn(5L);
        when(fileMetadataRepository.getTotalStorageUsedByOwner("testuser")).thenReturn(10485760L); // 10 MB
        when(s3Service.getBucketName()).thenReturn("test-bucket");

        StorageStatsResponse stats = fileStorageService.getStorageStats("testuser");

        assertEquals("testuser", stats.getOwner());
        assertEquals(5, stats.getTotalFiles());
        assertEquals(10485760L, stats.getTotalStorageBytes());
        assertEquals("10.00 MB", stats.getTotalStorageFormatted());
    }

    // ---- Helper ----

    private FileMetadata buildActiveMetadata(Long id, String owner) {
        return FileMetadata.builder()
                .id(id)
                .originalFileName("test-file.jpg")
                .s3Key(owner + "/12345_test-file.jpg")
                .bucketName("test-bucket")
                .contentType("image/jpeg")
                .fileSizeBytes(1024L)
                .owner(owner)
                .status(FileStatus.ACTIVE)
                .uploadedAt(LocalDateTime.now())
                .build();
    }
}
