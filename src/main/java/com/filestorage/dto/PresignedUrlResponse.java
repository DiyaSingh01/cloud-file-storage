package com.filestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    private Long fileId;
    private String originalFileName;
    private String presignedUrl;
    private LocalDateTime expiresAt;
    private int expiryMinutes;
    private String contentType;
    private Long fileSizeBytes;
}
