package com.filestorage.service;

import com.filestorage.exception.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * Handles all direct AWS S3 interactions:
 * upload, download, delete, and presigned URL generation.
 */
@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry-minutes:60}")
    private int presignedUrlExpiryMinutes;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Upload a file to S3.
     *
     * @param s3Key       The key (path) under which to store the file in S3
     * @param inputStream The file content
     * @param contentType MIME type of the file
     * @param fileSize    Size in bytes
     */
    public void uploadFile(String s3Key, InputStream inputStream, String contentType, long fileSize) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength(fileSize)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, fileSize));
            logger.info("File uploaded to S3. Key: {}, Size: {} bytes", s3Key, fileSize);

        } catch (S3Exception e) {
            logger.error("S3 upload failed for key: {}. Error: {}", s3Key, e.getMessage());
            throw new FileStorageException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download a file from S3 as an InputStream.
     *
     * @param s3Key The S3 key of the file
     * @return InputStream of the file content
     */
    public ResponseInputStream<GetObjectResponse> downloadFile(String s3Key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            logger.info("Downloading file from S3. Key: {}", s3Key);
            return s3Client.getObject(request);

        } catch (NoSuchKeyException e) {
            throw new com.filestorage.exception.FileNotFoundException(
                    "File not found in S3 with key: " + s3Key);
        } catch (S3Exception e) {
            logger.error("S3 download failed for key: {}. Error: {}", s3Key, e.getMessage());
            throw new FileStorageException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file from S3.
     *
     * @param s3Key The S3 key of the file to delete
     */
    public void deleteFile(String s3Key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(request);
            logger.info("File deleted from S3. Key: {}", s3Key);

        } catch (S3Exception e) {
            logger.error("S3 delete failed for key: {}. Error: {}", s3Key, e.getMessage());
            throw new FileStorageException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a presigned URL for temporary public access to a file.
     * The URL expires after the configured duration.
     *
     * @param s3Key The S3 key of the file
     * @return Presigned URL string
     */
    public String generatePresignedUrl(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            URL url = presigned.url();

            logger.info("Presigned URL generated for key: {} (expires in {} mins)", s3Key, presignedUrlExpiryMinutes);
            return url.toString();

        } catch (S3Exception e) {
            logger.error("Failed to generate presigned URL for key: {}", s3Key);
            throw new FileStorageException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a file exists in S3.
     */
    public boolean fileExists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            logger.warn("Could not check S3 existence for key {}: {}", s3Key, e.getMessage());
            return false;
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public int getPresignedUrlExpiryMinutes() {
        return presignedUrlExpiryMinutes;
    }
}
