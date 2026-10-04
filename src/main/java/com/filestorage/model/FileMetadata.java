package com.filestorage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata", indexes = {
        @Index(name = "idx_owner", columnList = "owner"),
        @Index(name = "idx_s3_key", columnList = "s3Key", unique = true),
        @Index(name = "idx_uploaded_at", columnList = "uploadedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false, unique = true)
    private String s3Key;           // The key used to store file in S3

    @Column(nullable = false)
    private String bucketName;

    @Column(nullable = false)
    private String contentType;     // MIME type

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private String owner;           // Username / user ID who uploaded

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    private LocalDateTime lastAccessedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FileStatus status = FileStatus.ACTIVE;

    @Column(length = 64)
    private String checksum;        // MD5 hash for integrity

    @Column(length = 500)
    private String tags;            // Comma-separated tags

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
