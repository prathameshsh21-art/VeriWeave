package com.veriweave.knowledge.repository;

import com.veriweave.knowledge.entity.SourceVersion;
import com.veriweave.knowledge.entity.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceVersionRepository extends JpaRepository<SourceVersion, Long> {

    @Query("SELECT v FROM SourceVersion v LEFT JOIN FETCH v.approvedBy WHERE v.source.id = :sourceId ORDER BY v.versionNumber DESC")
    List<SourceVersion> findAllBySourceIdOrderByVersionNumberDesc(@Param("sourceId") Long sourceId);

    @Query("SELECT v FROM SourceVersion v LEFT JOIN FETCH v.approvedBy WHERE v.id = :id AND v.source.id = :sourceId")
    Optional<SourceVersion> findByIdAndSourceId(@Param("id") Long id, @Param("sourceId") Long sourceId);

    Optional<SourceVersion> findFirstBySourceIdAndStatus(Long sourceId, VersionStatus status);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM SourceVersion v WHERE v.source.id = :sourceId")
    int findMaxVersionNumberBySourceId(@Param("sourceId") Long sourceId);
}
