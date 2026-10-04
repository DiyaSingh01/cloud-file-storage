package com.filestorage.dto;

import com.filestorage.model.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private Long id;
    private String originalFileName;
    private String s3Key;
    private String contentType;
    private Long fileSizeBytes;
    private String fileSizeFormatted;
    private String owner;
    private String description;
    private String tags;
    private String checksum;
    private FileStatus status;
    private LocalDateTime uploadedAt;
}
