package com.veriweave.analysis.service;

import com.veriweave.analysis.entity.AnalysisRun;
import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.pipeline.*;
import com.veriweave.analysis.repository.AnalysisRunRepository;
import com.veriweave.analysis.repository.FindingRepository;
import com.veriweave.audit.service.AuditService;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import com.veriweave.knowledge.entity.KnowledgeSource;
import com.veriweave.knowledge.entity.SourceVersion;
import com.veriweave.knowledge.entity.VersionStatus;
import com.veriweave.knowledge.repository.KnowledgeEntryRepository;
import com.veriweave.knowledge.repository.KnowledgeSourceRepository;
import com.veriweave.submission.entity.ContentVersion;
import com.veriweave.submission.entity.Submission;
import com.veriweave.submission.entity.SubmissionStatus;
import com.veriweave.submission.repository.ContentVersionRepository;
import com.veriweave.submission.repository.SubmissionRepository;
import com.veriweave.workspace.entity.User;
import com.veriweave.workspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisPipelineService {

    private final SubmissionRepository submissionRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeEntryRepository entryRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final FindingRepository findingRepository;
    private final UserRepository userRepository;
    private final TextSegmenter textSegmenter;
    private final AiClient aiClient;
    private final EvidenceValidator evidenceValidator;
    private final AuditService auditService;

    @Transactional
    public List<Finding> runAnalysis(Long workspaceId, Long submissionId, Long contentVersionId, Long userId) {
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));

        ContentVersion contentVersion = contentVersionRepository.findByIdAndSubmissionId(contentVersionId, submissionId)
                .orElseThrow(() -> new AccessDeniedException("Content version not found: " + contentVersionId));

        User user = userRepository.findById(userId).orElseThrow();

        // 1. Passage Segmentation with exact offsets
        String rawText = contentVersion.getRawText();
        List<Passage> passages = textSegmenter.segment(rawText);
        log.info("Segmented submission {} v{} into {} passages", submissionId, contentVersion.getVersionNumber(), passages.size());

        // 2. Retrieve ONLY CURRENT, active Knowledge Sources for this workspace
        List<KnowledgeSource> sources = sourceRepository.findAllActiveByWorkspaceId(workspaceId);
        List<KnowledgeEntry> candidateEntries = new ArrayList<>();
        Map<Long, KnowledgeEntry> candidateMap = new HashMap<>();

        for (KnowledgeSource source : sources) {
            SourceVersion currentVer = source.getCurrentVersion();
            if (currentVer != null && currentVer.getStatus() == VersionStatus.CURRENT) {
                List<KnowledgeEntry> entries = entryRepository.findAllBySourceVersionIdOrderByCodeAsc(currentVer.getId());
                for (KnowledgeEntry entry : entries) {
                    candidateEntries.add(entry);
                    candidateMap.put(entry.getId(), entry);
                }
            }
        }
        log.info("Retrieved {} active candidate knowledge entries across {} approved sources", candidateEntries.size(), sources.size());

        // 3. AI / Deterministic Model Proposal Phase
        List<ProposedFinding> proposedFindings = aiClient.proposeFindings(rawText, passages, candidateEntries);
        log.info("Model proposed {} initial candidate findings", proposedFindings.size());

        // 4. AnalysisRun Record Creation
        AnalysisRun run = AnalysisRun.builder()
                .contentVersion(contentVersion)
                .initiatedBy(user)
                .modelName(aiClient.getProviderName())
                .promptVersion("VeriWeave-v1-SchemaBound")
                .status("COMPLETED")
                .build();
        run = analysisRunRepository.save(run);

        // 5. Deterministic Evidence Validation Phase
        List<Finding> savedFindings = new ArrayList<>();
        int verifiedCount = 0;
        int rejectedCount = 0;

        for (ProposedFinding proposal : proposedFindings) {
            Finding validated = evidenceValidator.validateAndClassify(proposal, rawText, workspaceId, candidateMap);
            if (validated != null) {
                validated.setAnalysisRun(run);
                validated.setContentVersion(contentVersion);
                
                // Do not display rejected findings to users; save for internal audit/evaluation
                Finding saved = findingRepository.save(validated);
                if (saved.getClassification() != FindingClassification.REJECTED_BY_VALIDATOR) {
                    savedFindings.add(saved);
                    if (saved.getClassification() == FindingClassification.VERIFIED) {
                        verifiedCount++;
                    }
                } else {
                    rejectedCount++;
                }
            }
        }

        // 6. Update Submission Status
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            submission.setStatus(SubmissionStatus.IN_REVIEW);
            submissionRepository.save(submission);
        }

        // 7. Audit Event
        auditService.logEvent(submission.getWorkspace(), user, "RUN_ANALYSIS", "AnalysisRun", run.getId(),
                String.format("Analysis completed on v%d. Verified findings: %d, Rejected by validator: %d",
                        contentVersion.getVersionNumber(), verifiedCount, rejectedCount));

        log.info("Analysis run {} completed. Verified: {}, Rejected: {}", run.getId(), verifiedCount, rejectedCount);
        return savedFindings;
    }

    @Transactional(readOnly = true)
    public List<Finding> getFindingsForVersion(Long workspaceId, Long submissionId, Long contentVersionId) {
        submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));

        contentVersionRepository.findByIdAndSubmissionId(contentVersionId, submissionId)
                .orElseThrow(() -> new AccessDeniedException("Content version not found: " + contentVersionId));

        // Return all non-rejected findings (verified, needs_judgement, advisory)
        return findingRepository.findAllByContentVersionIdExcluding(contentVersionId, FindingClassification.REJECTED_BY_VALIDATOR);
    }
}
