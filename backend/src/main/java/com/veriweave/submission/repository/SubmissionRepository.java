package com.veriweave.submission.repository;

import com.veriweave.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.currentVersion LEFT JOIN FETCH s.createdBy WHERE s.workspace.id = :workspaceId ORDER BY s.updatedAt DESC")
    List<Submission> findAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.currentVersion LEFT JOIN FETCH s.createdBy WHERE s.workspace.id = :workspaceId AND s.createdBy.id = :userId ORDER BY s.updatedAt DESC")
    List<Submission> findAllByWorkspaceIdAndCreatedById(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.currentVersion LEFT JOIN FETCH s.createdBy WHERE s.id = :id AND s.workspace.id = :workspaceId")
    Optional<Submission> findByIdAndWorkspaceId(@Param("id") Long id, @Param("workspaceId") Long workspaceId);
}
