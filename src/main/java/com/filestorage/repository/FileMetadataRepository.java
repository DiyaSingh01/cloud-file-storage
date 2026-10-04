package com.filestorage.repository;

import com.filestorage.model.FileMetadata;
import com.filestorage.model.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findByOwnerAndStatus(String owner, FileStatus status, Pageable pageable);

    Page<FileMetadata> findByStatus(FileStatus status, Pageable pageable);

    Optional<FileMetadata> findByS3Key(String s3Key);

    Optional<FileMetadata> findByIdAndStatus(Long id, FileStatus status);

    boolean existsByS3Key(String s3Key);

    @Query("SELECT f FROM FileMetadata f WHERE f.owner = :owner AND f.status = 'ACTIVE' " +
           "AND (:contentType IS NULL OR f.contentType LIKE :contentType%) " +
           "AND (:search IS NULL OR LOWER(f.originalFileName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<FileMetadata> searchFiles(@Param("owner") String owner,
                                   @Param("contentType") String contentType,
                                   @Param("search") String search,
                                   Pageable pageable);

    @Query("SELECT COALESCE(SUM(f.fileSizeBytes), 0) FROM FileMetadata f WHERE f.owner = :owner AND f.status = 'ACTIVE'")
    Long getTotalStorageUsedByOwner(@Param("owner") String owner);

    @Query("SELECT COUNT(f) FROM FileMetadata f WHERE f.owner = :owner AND f.status = 'ACTIVE'")
    long countActiveFilesByOwner(@Param("owner") String owner);
}
