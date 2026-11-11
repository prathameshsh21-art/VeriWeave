package com.veriweave.submission.repository;

import com.veriweave.submission.entity.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    @Query("SELECT cv FROM ContentVersion cv LEFT JOIN FETCH cv.createdBy WHERE cv.submission.id = :submissionId ORDER BY cv.versionNumber DESC")
    List<ContentVersion> findAllBySubmissionIdOrderByVersionNumberDesc(@Param("submissionId") Long submissionId);

    @Query("SELECT cv FROM ContentVersion cv LEFT JOIN FETCH cv.createdBy WHERE cv.id = :id AND cv.submission.id = :submissionId")
    Optional<ContentVersion> findByIdAndSubmissionId(@Param("id") Long id, @Param("submissionId") Long submissionId);

    @Query("SELECT COALESCE(MAX(cv.versionNumber), 0) FROM ContentVersion cv WHERE cv.submission.id = :submissionId")
    int findMaxVersionNumberBySubmissionId(@Param("submissionId") Long submissionId);
}
