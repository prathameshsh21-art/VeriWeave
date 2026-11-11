package com.veriweave.review.repository;

import com.veriweave.review.entity.FindingResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FindingResponseRepository extends JpaRepository<FindingResponse, Long> {
    Optional<FindingResponse> findByFindingId(Long findingId);
}
