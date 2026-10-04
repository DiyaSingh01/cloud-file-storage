package com.filestorage.service;

import com.filestorage.dto.FileUploadResponse;
import com.filestorage.dto.PresignedUrlResponse;
import com.filestorage.dto.StorageStatsResponse;
import com.filestorage.exception.FileNotFoundException;
import com.filestorage.exception.FileStorageException;
import com.filestorage.exception.InvalidFileException;
import com.filestorage.model.FileMetadata;
import com.filestorage.model.FileStatus;
import com.filestorage.repository.FileMetadataRepository;
import com.filestorage.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final S3Service s3Service;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${storage.allowed-mime-types}")
    private String allowedMimeTypes;

    @Value("${storage.max-file-size-bytes:52428800}")
    private long maxFileSizeBytes;

    @Value("${aws.region}")
    private String awsRegion;

    public FileStorageService(S3Service s3Service, FileMetadataRepository fileMetadataRepository) {
        this.s3Service = s3Service;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    /**
     * Upload a file to S3 and save its metadata to MySQL.
     */
    public FileUploadResponse uploadFile(MultipartFile file, String owner, String description, String tags) {
        // Validate file
        validateFile(file);

        String originalFileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();

        // Build unique S3 key
        String s3Key = FileUtil.buildS3Key(owner, originalFileName);

        try {
            // Compute checksum
            byte[] fileBytes = file.getBytes();
            String checksum = FileUtil.computeMd5(fileBytes);

            // Upload to S3
            s3Service.uploadFile(s3Key, file.getInputStream(), contentType, fileSize);

            // Save metadata to MySQL
            FileMetadata metadata = FileMetadata.builder()
                    .originalFileName(originalFileName)
                    .s3Key(s3Key)
                    .bucketName(s3Service.getBucketName())
                    .contentType(contentType)
                    .fileSizeBytes(fileSize)
                    .owner(owner)
                    .description(description)
                    .tags(tags)
                    .checksum(checksum)
                    .status(FileStatus.ACTIVE)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            FileMetadata saved = fileMetadataRepository.save(metadata);
            logger.info("File uploaded successfully. id={}, owner={}, size={}", saved.getId(), owner, fileSize);

            return toUploadResponse(saved);

        } catch (IOException e) {
            // Clean up S3 if metadata save was reached but error occurred
            try { s3Service.deleteFile(s3Key); } catch (Exception ignored) {}
            throw new FileStorageException("Failed to read file content: " + e.getMessage(), e);
        }
    }

    /**
     * Download a file directly from S3.
     * Returns the S3 response stream and metadata.
     */
    @Transactional(readOnly = true)
    public FileDownloadResult downloadFile(Long fileId, String requestingOwner) {
        FileMetadata metadata = getActiveFile(fileId);
        checkOwnership(metadata, requestingOwner);

        // Update last accessed time
        metadata.setLastAccessedAt(LocalDateTime.now());
        fileMetadataRepository.save(metadata);

        ResponseInputStream<GetObjectResponse> s3Stream = s3Service.downloadFile(metadata.getS3Key());
        return new FileDownloadResult(metadata, s3Stream);
    }

    /**
     * Generate a presigned URL for temporary access to the file.
     */
    @Transactional(readOnly = true)
    public PresignedUrlResponse generatePresignedUrl(Long fileId, String requestingOwner) {
        FileMetadata metadata = getActiveFile(fileId);
        checkOwnership(metadata, requestingOwner);

        String presignedUrl = s3Service.generatePresignedUrl(metadata.getS3Key());
        int expiryMinutes = s3Service.getPresignedUrlExpiryMinutes();

        return PresignedUrlResponse.builder()
                .fileId(metadata.getId())
                .originalFileName(metadata.getOriginalFileName())
                .presignedUrl(presignedUrl)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .expiryMinutes(expiryMinutes)
                .contentType(metadata.getContentType())
                .fileSizeBytes(metadata.getFileSizeBytes())
                .build();
    }

    /**
     * Soft-delete a file: removes from S3, marks metadata as DELETED.
     */
    public void deleteFile(Long fileId, String requestingOwner) {
        FileMetadata metadata = getActiveFile(fileId);
        checkOwnership(metadata, requestingOwner);

        // Delete from S3
        s3Service.deleteFile(metadata.getS3Key());

        // Soft delete in DB
        metadata.setStatus(FileStatus.DELETED);
        fileMetadataRepository.save(metadata);

        logger.info("File soft-deleted. id={}, s3Key={}", fileId, metadata.getS3Key());
    }

    /**
     * List files for a given owner with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<FileUploadResponse> listFiles(String owner, String contentType, String search, Pageable pageable) {
        return fileMetadataRepository.searchFiles(owner, contentType, search, pageable)
                .map(this::toUploadResponse);
    }

    /**
     * Get metadata for a single file.
     */
    @Transactional(readOnly = true)
    public FileUploadResponse getFileMetadata(Long fileId, String requestingOwner) {
        FileMetadata metadata = getActiveFile(fileId);
        checkOwnership(metadata, requestingOwner);
        return toUploadResponse(metadata);
    }

    /**
     * Get storage usage stats for an owner.
     */
    @Transactional(readOnly = true)
    public StorageStatsResponse getStorageStats(String owner) {
        long totalFiles = fileMetadataRepository.countActiveFilesByOwner(owner);
        Long totalBytes = fileMetadataRepository.getTotalStorageUsedByOwner(owner);
        if (totalBytes == null) totalBytes = 0L;

        return StorageStatsResponse.builder()
                .owner(owner)
                .totalFiles(totalFiles)
                .totalStorageBytes(totalBytes)
                .totalStorageFormatted(FileUtil.formatFileSize(totalBytes))
                .bucketName(s3Service.getBucketName())
                .awsRegion(awsRegion)
                .build();
    }

    // ---- Private helpers ----

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException(String.format(
                    "File size %.2f MB exceeds maximum allowed size of %.2f MB",
                    file.getSize() / (1024.0 * 1024),
                    maxFileSizeBytes / (1024.0 * 1024)
            ));
        }

        String contentType = file.getContentType();
        List<String> allowed = Arrays.asList(allowedMimeTypes.split(","));
        if (contentType == null || !allowed.contains(contentType.trim())) {
            throw new InvalidFileException(
                    "File type '" + contentType + "' is not allowed. Allowed types: " + allowedMimeTypes);
        }
    }

    private FileMetadata getActiveFile(Long fileId) {
        return fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + fileId));
    }

    private void checkOwnership(FileMetadata metadata, String requestingOwner) {
        if (!metadata.getOwner().equals(requestingOwner)) {
            throw new FileStorageException("Access denied: you do not own this file");
        }
    }

    private FileUploadResponse toUploadResponse(FileMetadata m) {
        return FileUploadResponse.builder()
                .id(m.getId())
                .originalFileName(m.getOriginalFileName())
                .s3Key(m.getS3Key())
                .contentType(m.getContentType())
                .fileSizeBytes(m.getFileSizeBytes())
                .fileSizeFormatted(FileUtil.formatFileSize(m.getFileSizeBytes()))
                .owner(m.getOwner())
                .description(m.getDescription())
                .tags(m.getTags())
                .checksum(m.getChecksum())
                .status(m.getStatus())
                .uploadedAt(m.getUploadedAt())
                .build();
    }

    // Inner class to bundle download result
    public record FileDownloadResult(FileMetadata metadata,
                                     ResponseInputStream<GetObjectResponse> s3Stream) {}
}
