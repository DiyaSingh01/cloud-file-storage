package com.filestorage.model;

public enum FileStatus {
    ACTIVE,     // File exists in S3 and is accessible
    DELETED,    // Soft-deleted (removed from S3, metadata retained)
    ARCHIVED    // Moved to S3 Glacier (future feature)
}
