package com.safalifter.filestorage.repository;

import com.safalifter.filestorage.model.FileReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileReferenceRepository extends JpaRepository<FileReference, Long> {
    List<FileReference> findByFileId(String fileId);

    Optional<FileReference> findByFileIdAndEntityTypeAndEntityId(String fileId, String entityType, String entityId);

    List<FileReference> findByEntityTypeAndEntityId(String entityType, String entityId);

    long countByFileId(String fileId);

    @Query("SELECT COUNT(DISTINCT fr.fileId) FROM file_references fr WHERE fr.entityType = :entityType AND fr.entityId = :entityId")
    long countFilesByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    void deleteByFileIdAndEntityTypeAndEntityId(String fileId, String entityType, String entityId);

    void deleteByEntityTypeAndEntityId(String entityType, String entityId);
}
