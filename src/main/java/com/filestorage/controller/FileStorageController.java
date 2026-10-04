package com.filestorage.controller;

import com.filestorage.dto.ApiResponse;
import com.filestorage.dto.FileUploadResponse;
import com.filestorage.dto.PresignedUrlResponse;
import com.filestorage.dto.StorageStatsResponse;
import com.filestorage.exception.FileNotFoundException;
import com.filestorage.exception.FileStorageException;
import com.filestorage.exception.InvalidFileException;
import com.filestorage.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileStorageController {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageController.class);

    private final FileStorageService fileStorageService;

    public FileStorageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * POST /api/v1/files/upload
     * Upload a file to S3.
     * The 'owner' header identifies the user (replace with JWT in production).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags) {

        try {
            FileUploadResponse response = fileStorageService.uploadFile(file, owner, description, tags);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response,
                            "File uploaded successfully. ID: " + response.getId()));
        } catch (InvalidFileException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/files/{id}/download
     * Download a file directly from S3 (streams the content).
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadFile(
            @PathVariable Long id,
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner) {

        try {
            FileStorageService.FileDownloadResult result = fileStorageService.downloadFile(id, owner);
            String contentType = result.metadata().getContentType();
            String fileName = result.metadata().getOriginalFileName();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.metadata().getFileSizeBytes()))
                    .body(new InputStreamResource(result.s3Stream()));

        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/files/{id}/presigned-url
     * Generate a temporary presigned URL for accessing a file without auth headers.
     */
    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @PathVariable Long id,
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner) {

        try {
            PresignedUrlResponse response = fileStorageService.generatePresignedUrl(id, owner);
            return ResponseEntity.ok(ApiResponse.success(response,
                    "Presigned URL generated. Expires in " + response.getExpiryMinutes() + " minutes."));
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/v1/files/{id}
     * Delete a file from S3 and soft-delete its metadata.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long id,
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner) {

        try {
            fileStorageService.deleteFile(id, owner);
            return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/files
     * List all files for the requesting owner with optional filters and pagination.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FileUploadResponse>>> listFiles(
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "uploadedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<FileUploadResponse> result = fileStorageService.listFiles(owner, contentType, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(result,
                "Found " + result.getTotalElements() + " files"));
    }

    /**
     * GET /api/v1/files/{id}
     * Get metadata for a single file.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileUploadResponse>> getFileMetadata(
            @PathVariable Long id,
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner) {

        try {
            FileUploadResponse response = fileStorageService.getFileMetadata(id, owner);
            return ResponseEntity.ok(ApiResponse.success(response, "File metadata retrieved"));
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/files/stats
     * Get storage usage statistics for the requesting owner.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StorageStatsResponse>> getStorageStats(
            @RequestHeader(value = "X-Owner", defaultValue = "default-user") String owner) {

        StorageStatsResponse stats = fileStorageService.getStorageStats(owner);
        return ResponseEntity.ok(ApiResponse.success(stats, "Storage stats retrieved for: " + owner));
    }
}
