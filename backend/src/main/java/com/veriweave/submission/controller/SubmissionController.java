package com.veriweave.submission.controller;

import com.veriweave.analysis.dto.AnalysisRunDto;
import com.veriweave.analysis.dto.AnalysisResponseDto;
import com.veriweave.analysis.dto.FindingDto;
import com.veriweave.analysis.dto.FindingTraceabilityDto;
import com.veriweave.analysis.entity.AnalysisRun;
import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.repository.AnalysisRunRepository;
import com.veriweave.analysis.repository.FindingRepository;
import com.veriweave.analysis.service.AnalysisPipelineService;
import com.veriweave.audit.dto.AuditEventDto;
import com.veriweave.audit.entity.AuditEvent;
import com.veriweave.audit.repository.AuditEventRepository;
import com.veriweave.common.ApiResponse;
import com.veriweave.knowledge.entity.KnowledgeEntry;
import com.veriweave.knowledge.entity.KnowledgeSource;
import com.veriweave.knowledge.entity.SourceVersion;
import com.veriweave.review.dto.CreatorResponseRequest;
import com.veriweave.review.dto.FindingResponseDto;
import com.veriweave.review.dto.ReviewDecisionDto;
import com.veriweave.review.dto.ReviewDecisionRequest;
import com.veriweave.review.entity.FindingResponse;
import com.veriweave.review.entity.ReviewDecision;
import com.veriweave.review.repository.FindingResponseRepository;
import com.veriweave.review.repository.ReviewDecisionRepository;
import com.veriweave.review.service.ReviewService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.submission.dto.ContentVersionDto;
import com.veriweave.submission.dto.CreateRevisionRequest;
import com.veriweave.submission.dto.CreateSubmissionRequest;
import com.veriweave.submission.dto.SubmissionDetailDto;
import com.veriweave.submission.dto.SubmissionDto;
import com.veriweave.submission.entity.ContentVersion;
import com.veriweave.submission.entity.Submission;
import com.veriweave.submission.repository.SubmissionRepository;
import com.veriweave.submission.service.SubmissionService;
import com.veriweave.workspace.entity.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AnalysisPipelineService pipelineService;
    private final ReviewService reviewService;
    private final WorkspaceSecurityService workspaceSecurity;
    private final FindingResponseRepository findingResponseRepository;
    private final SubmissionRepository submissionRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final FindingRepository findingRepository;
    private final ReviewDecisionRepository reviewDecisionRepository;
    private final AuditEventRepository auditEventRepository;

    private Submission validateSubmissionAccess(Long workspaceId, Long submissionId, UserPrincipal principal) {
        var membership = workspaceSecurity.requireMembership(workspaceId, principal);
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));

        if (membership.getRole() == Role.CREATOR) {
            if (submission.getCreatedBy() == null || !submission.getCreatedBy().getId().equals(principal.getId())) {
                throw new AccessDeniedException("Access denied: Creators can only access their own submissions.");
            }
        }
        return submission;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionDto>> createSubmission(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.MANAGER, Role.CREATOR);
        SubmissionDto dto = submissionService.createSubmission(workspaceId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Submission created", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubmissionDto>>> listSubmissions(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        var membership = workspaceSecurity.requireMembership(workspaceId, principal);
        boolean isCreatorOnly = membership.getRole() == Role.CREATOR;
        List<SubmissionDto> list = submissionService.listSubmissions(workspaceId, principal.getId(), isCreatorOnly);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping({"/{submissionId}", "/{submissionId}/detail"})
    public ResponseEntity<ApiResponse<SubmissionDetailDto>> getSubmission(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Submission sub = validateSubmissionAccess(workspaceId, submissionId, principal);

        ContentVersion cv = sub.getCurrentVersion();
        Long versionId = cv != null ? cv.getId() : null;

        List<FindingDto> findingDtos = List.of();
        List<FindingResponseDto> responseDtos = new ArrayList<>();
        AnalysisRunDto latestRunDto = null;

        if (versionId != null) {
            List<Finding> findings = pipelineService.getFindingsForVersion(workspaceId, submissionId, versionId);
            findingDtos = findings.stream().map(this::toFindingDto).collect(Collectors.toList());

            for (Finding f : findings) {
                findingResponseRepository.findByFindingId(f.getId())
                        .ifPresent(r -> responseDtos.add(reviewService.toResponseDto(r)));
            }

            List<AnalysisRun> runs = analysisRunRepository.findAllByContentVersionIdOrderByCreatedAtDesc(versionId);
            if (!runs.isEmpty()) {
                AnalysisRun latest = runs.get(0);
                long verifiedCount = findingDtos.stream().filter(f -> f.getClassification() == FindingClassification.VERIFIED).count();
                latestRunDto = AnalysisRunDto.builder()
                        .id(latest.getId())
                        .contentVersionId(latest.getContentVersion().getId())
                        .modelName(latest.getModelName())
                        .promptVersion(latest.getPromptVersion())
                        .status(latest.getStatus())
                        .passagesCount(1)
                        .verifiedFindingsCount((int) verifiedCount)
                        .rejectedFindingsCount(0)
                        .completedAt(latest.getCreatedAt())
                        .createdAt(latest.getCreatedAt())
                        .build();
            }
        }

        ContentVersionDto cvDto = cv != null ? ContentVersionDto.builder()
                .id(cv.getId())
                .submissionId(sub.getId())
                .versionNumber(cv.getVersionNumber())
                .rawText(cv.getRawText())
                .checksum(cv.getChecksum())
                .createdBy(cv.getCreatedBy() != null ? cv.getCreatedBy().getFullName() : null)
                .createdAt(cv.getCreatedAt())
                .build() : null;

        SubmissionDetailDto.CreatorDto creatorDto = sub.getCreatedBy() != null ? SubmissionDetailDto.CreatorDto.builder()
                .id(sub.getCreatedBy().getId())
                .fullName(sub.getCreatedBy().getFullName())
                .email(sub.getCreatedBy().getEmail())
                .build() : null;

        SubmissionDetailDto detailDto = SubmissionDetailDto.builder()
                .id(sub.getId())
                .workspaceId(workspaceId)
                .title(sub.getTitle())
                .campaignName("General Campaign")
                .status(sub.getStatus())
                .createdBy(creatorDto)
                .currentVersion(cvDto)
                .activeVersion(cvDto)
                .currentVersionNumber(cv != null ? cv.getVersionNumber() : 1)
                .findings(findingDtos)
                .responses(responseDtos)
                .latestAnalysisRun(latestRunDto)
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(detailDto));
    }

    @PostMapping("/{submissionId}/revisions")
    public ResponseEntity<ApiResponse<SubmissionDto>> createRevision(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @Valid @RequestBody CreateRevisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateSubmissionAccess(workspaceId, submissionId, principal);
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.MANAGER, Role.CREATOR);
        SubmissionDto dto = submissionService.createRevision(workspaceId, submissionId, request.getRawText(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Revision created", dto));
    }

    @GetMapping("/{submissionId}/versions")
    public ResponseEntity<ApiResponse<List<ContentVersionDto>>> listVersions(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateSubmissionAccess(workspaceId, submissionId, principal);
        List<ContentVersionDto> versions = submissionService.listVersions(workspaceId, submissionId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    // --- Analysis Pipeline Trigger ---

    @PostMapping({"/{submissionId}/versions/{versionId}/analyze", "/{submissionId}/analyze"})
    public ResponseEntity<ApiResponse<AnalysisResponseDto>> runAnalysis(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @PathVariable(required = false) Long versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Submission sub = validateSubmissionAccess(workspaceId, submissionId, principal);
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.MANAGER, Role.CREATOR);
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            targetVersionId = sub.getCurrentVersion() != null ? sub.getCurrentVersion().getId() : 1L;
        }
        List<Finding> findings = pipelineService.runAnalysis(workspaceId, submissionId, targetVersionId, principal.getId());
        List<FindingDto> dtos = findings.stream().map(this::toFindingDto).collect(Collectors.toList());

        Optional<AnalysisRun> latestRun = analysisRunRepository.findFirstByContentVersionIdOrderByCreatedAtDesc(targetVersionId);
        Long runId = latestRun.map(AnalysisRun::getId).orElse(null);

        AnalysisResponseDto response = AnalysisResponseDto.builder()
                .runId(runId)
                .status("COMPLETED")
                .verifiedFindingsCount(dtos.size())
                .rejectedFindingsCount(0)
                .findings(dtos)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Analysis completed with " + findings.size() + " findings", response));
    }

    @GetMapping({"/{submissionId}/versions/{versionId}/findings", "/{submissionId}/findings"})
    public ResponseEntity<ApiResponse<List<FindingDto>>> getFindings(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @PathVariable(required = false) Long versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Submission sub = validateSubmissionAccess(workspaceId, submissionId, principal);
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            targetVersionId = sub.getCurrentVersion() != null ? sub.getCurrentVersion().getId() : 1L;
        }
        List<Finding> findings = pipelineService.getFindingsForVersion(workspaceId, submissionId, targetVersionId);
        List<FindingDto> dtos = findings.stream().map(this::toFindingDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    // --- Creator Response ---

    @PostMapping({"/{submissionId}/findings/{findingId}/respond", "/{submissionId}/findings/{findingId}/responses"})
    public ResponseEntity<ApiResponse<FindingResponseDto>> respondToFinding(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @PathVariable Long findingId,
            @Valid @RequestBody CreatorResponseRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateSubmissionAccess(workspaceId, submissionId, principal);
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.CREATOR);
        FindingResponseDto dto = reviewService.respondToFinding(workspaceId, submissionId, findingId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Response recorded", dto));
    }

    // --- Reviewer Decision ---

    @PostMapping({"/{submissionId}/versions/{versionId}/decide", "/{submissionId}/decisions", "/{submissionId}/decide"})
    public ResponseEntity<ApiResponse<ReviewDecisionDto>> makeReviewDecision(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @PathVariable(required = false) Long versionId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.REVIEWER);
        Submission sub = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            targetVersionId = sub.getCurrentVersion() != null ? sub.getCurrentVersion().getId() : 1L;
        }
        ReviewDecisionDto dto = reviewService.recordReviewDecision(workspaceId, submissionId, targetVersionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Review decision saved", dto));
    }

    @GetMapping("/{submissionId}/decisions")
    public ResponseEntity<ApiResponse<List<ReviewDecisionDto>>> getDecisions(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateSubmissionAccess(workspaceId, submissionId, principal);
        List<ReviewDecisionDto> decisions = reviewService.getSubmissionDecisions(workspaceId, submissionId);
        return ResponseEntity.ok(ApiResponse.success(decisions));
    }

    @GetMapping("/{submissionId}/findings/{findingId}/trace")
    public ResponseEntity<ApiResponse<FindingTraceabilityDto>> getFindingTraceability(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @PathVariable Long findingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Submission submission = validateSubmissionAccess(workspaceId, submissionId, principal);

        Finding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new AccessDeniedException("Finding not found: " + findingId));

        if (!finding.getContentVersion().getSubmission().getId().equals(submissionId)) {
            throw new AccessDeniedException("Finding does not belong to submission: " + submissionId);
        }

        ContentVersion cv = finding.getContentVersion();
        KnowledgeEntry entry = finding.getKnowledgeEntry();
        SourceVersion sv = finding.getSourceVersion() != null ? finding.getSourceVersion()
                : (entry != null ? entry.getSourceVersion() : null);
        KnowledgeSource source = sv != null ? sv.getSource() : null;

        Optional<FindingResponse> responseOpt = findingResponseRepository.findByFindingId(findingId);
        Optional<ReviewDecision> decisionOpt = reviewDecisionRepository.findFirstByContentVersionId(cv.getId());

        List<AuditEvent> auditEvents = auditEventRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        List<AuditEventDto> traceAuditTrail = auditEvents.stream()
                .filter(a -> (a.getEntityType() != null && a.getEntityType().equalsIgnoreCase("SUBMISSION") && a.getEntityId().equals(submissionId))
                        || (a.getEntityType() != null && a.getEntityType().equalsIgnoreCase("FINDING") && a.getEntityId().equals(findingId)))
                .map(this::toAuditDto)
                .collect(Collectors.toList());

        FindingTraceabilityDto traceDto = FindingTraceabilityDto.builder()
                // 1. Submitted Sentence & Content Context
                .submissionId(submission.getId())
                .submissionTitle(submission.getTitle())
                .contentVersionId(cv.getId())
                .contentVersionNumber(cv.getVersionNumber())
                .submissionAuthor(submission.getCreatedBy() != null ? submission.getCreatedBy().getFullName() : "Unknown Author")
                .submissionCreatedAt(submission.getCreatedAt())
                .rawText(cv.getRawText())

                // 2. Deterministic Finding
                .findingId(finding.getId())
                .analysisRunId(finding.getAnalysisRun() != null ? finding.getAnalysisRun().getId() : null)
                .findingType(finding.getType())
                .category(finding.getCategory())
                .classification(finding.getClassification())
                .severity(finding.getSeverity())
                .passageStart(finding.getPassageStart())
                .passageEnd(finding.getPassageEnd())
                .passageQuote(finding.getPassageQuote())
                .explanation(finding.getExplanation())
                .suggestedEdit(finding.getSuggestedEdit())
                .confidence(finding.getConfidence())
                .findingCreatedAt(finding.getCreatedAt())

                // 3. Approved Guideline & Evidence Provenance
                .knowledgeEntryId(entry != null ? entry.getId() : null)
                .ruleCode(entry != null ? entry.getCode() : "ADVISORY")
                .ruleText(entry != null ? entry.getText() : null)
                .pageOrSectionReference(entry != null ? entry.getPageOrSectionReference() : null)
                .sourceId(source != null ? source.getId() : null)
                .sourceTitle(source != null ? source.getTitle() : null)
                .sourceType(source != null && source.getType() != null ? source.getType().name() : null)
                .sourceVersionNumber(sv != null ? sv.getVersionNumber() : null)
                .sourceVersionChecksum(sv != null ? sv.getChecksum() : null)
                .sourceStatus(sv != null && sv.getStatus() != null ? sv.getStatus().name() : null)
                .evidenceQuote(finding.getEvidenceQuote())

                // 4. Creator Response & Revision
                .responseId(responseOpt.map(FindingResponse::getId).orElse(null))
                .creatorAction(responseOpt.map(FindingResponse::getAction).orElse(null))
                .creatorReason(responseOpt.map(FindingResponse::getReason).orElse(null))
                .creatorModifiedText(responseOpt.map(FindingResponse::getModifiedText).orElse(null))
                .creatorRespondedBy(responseOpt.map(r -> r.getUser() != null ? r.getUser().getFullName() : null).orElse(null))
                .creatorRespondedAt(responseOpt.map(FindingResponse::getCreatedAt).orElse(null))

                // 5. Reviewer Decision
                .decisionId(decisionOpt.map(ReviewDecision::getId).orElse(null))
                .reviewerDecision(decisionOpt.map(ReviewDecision::getDecision).orElse(null))
                .reviewerName(decisionOpt.map(d -> d.getReviewer() != null ? d.getReviewer().getFullName() : null).orElse(null))
                .reviewerComment(decisionOpt.map(ReviewDecision::getComment).orElse(null))
                .overrideReason(decisionOpt.map(ReviewDecision::getOverrideReason).orElse(null))
                .decidedAt(decisionOpt.map(ReviewDecision::getCreatedAt).orElse(null))

                // 6. Complete Immutable Audit Trail
                .auditTrail(traceAuditTrail)
                .build();

        return ResponseEntity.ok(ApiResponse.success(traceDto));
    }

    private AuditEventDto toAuditDto(AuditEvent event) {
        AuditEventDto.ActorDto actorDto = null;
        if (event.getActor() != null) {
            actorDto = AuditEventDto.ActorDto.builder()
                    .id(event.getActor().getId())
                    .email(event.getActor().getEmail())
                    .fullName(event.getActor().getFullName())
                    .build();
        }
        return AuditEventDto.builder()
                .id(event.getId())
                .workspaceId(event.getWorkspace() != null ? event.getWorkspace().getId() : null)
                .actor(actorDto)
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .timestamp(event.getCreatedAt())
                .build();
    }

    private FindingDto toFindingDto(Finding f) {
        Optional<FindingResponse> resp = findingResponseRepository.findByFindingId(f.getId());
        return FindingDto.builder()
                .id(f.getId())
                .contentVersionId(f.getContentVersion().getId())
                .knowledgeEntryId(f.getKnowledgeEntry() != null ? f.getKnowledgeEntry().getId() : null)
                .ruleCode(f.getKnowledgeEntry() != null ? f.getKnowledgeEntry().getCode() : "ADVISORY")
                .sourceTitle(f.getSourceVersion() != null ? f.getSourceVersion().getSource().getTitle() : null)
                .sourceVersionNumber(f.getSourceVersion() != null ? f.getSourceVersion().getVersionNumber() : null)
                .type(f.getType())
                .category(f.getCategory())
                .classification(f.getClassification())
                .severity(f.getSeverity())
                .passageStart(f.getPassageStart())
                .passageEnd(f.getPassageEnd())
                .passageQuote(f.getPassageQuote())
                .evidenceQuote(f.getEvidenceQuote())
                .explanation(f.getExplanation())
                .suggestedEdit(f.getSuggestedEdit())
                .confidence(f.getConfidence())
                .response(resp.map(reviewService::toResponseDto).orElse(null))
                .createdAt(f.getCreatedAt())
                .build();
    }
}
