package com.veriweave.review.repository;

import com.veriweave.review.entity.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {

    @Query("SELECT rd FROM ReviewDecision rd LEFT JOIN FETCH rd.reviewer WHERE rd.submission.id = :submissionId ORDER BY rd.createdAt DESC")
    List<ReviewDecision> findAllBySubmissionIdOrderByCreatedAtDesc(@Param("submissionId") Long submissionId);

    @Query("SELECT rd FROM ReviewDecision rd LEFT JOIN FETCH rd.reviewer WHERE rd.contentVersion.id = :versionId ORDER BY rd.createdAt DESC")
    Optional<ReviewDecision> findFirstByContentVersionId(@Param("versionId") Long versionId);

    boolean existsByContentVersionId(Long versionId);
}
