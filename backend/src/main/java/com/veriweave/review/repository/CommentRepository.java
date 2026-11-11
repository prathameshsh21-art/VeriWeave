package com.veriweave.review.repository;

import com.veriweave.review.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.submission.id = :submissionId ORDER BY c.createdAt ASC")
    List<Comment> findAllBySubmissionIdOrderByCreatedAtAsc(@Param("submissionId") Long submissionId);
}
