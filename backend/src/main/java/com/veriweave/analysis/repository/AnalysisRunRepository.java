package com.veriweave.analysis.repository;

import com.veriweave.analysis.entity.AnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    List<AnalysisRun> findAllByContentVersionIdOrderByCreatedAtDesc(Long contentVersionId);
    Optional<AnalysisRun> findFirstByContentVersionIdOrderByCreatedAtDesc(Long contentVersionId);
}
