-- Reference SQL — Spring Boot auto-creates tables via JPA
-- Run manually only if needed

CREATE DATABASE IF NOT EXISTS filestoragedb;
USE filestoragedb;

CREATE TABLE IF NOT EXISTS file_metadata (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL UNIQUE,
    bucket_name     VARCHAR(100) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    owner           VARCHAR(100) NOT NULL,
    description     TEXT,
    uploaded_at     DATETIME NOT NULL,
    last_accessed_at DATETIME,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    checksum        VARCHAR(64),
    tags            VARCHAR(500),
    INDEX idx_owner (owner),
    INDEX idx_status (status),
    INDEX idx_uploaded_at (uploaded_at)
);
