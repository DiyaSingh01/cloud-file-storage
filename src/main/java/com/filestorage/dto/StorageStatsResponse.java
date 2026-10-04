package com.filestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatsResponse {

    private String owner;
    private long totalFiles;
    private long totalStorageBytes;
    private String totalStorageFormatted;
    private String bucketName;
    private String awsRegion;
}
