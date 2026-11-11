package com.veriweave.submission.controller;

import com.veriweave.analysis.dto.AnalysisRunDto;
import com.veriweave.analysis.dto.AnalysisResponseDto;
import com.veriweave.analysis.dto.FindingDto;
import com.veriweave.analysis.entity.AnalysisRun;
import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.repository.AnalysisRunRepository;
import com.veriweave.analysis.service.AnalysisPipelineService;
import com.veriweave.common.ApiResponse;
import com.veriweave.review.dto.CreatorResponseRequest;
import com.veriweave.review.dto.FindingResponseDto;
import com.veriweave.review.dto.ReviewDecisionDto;
import com.veriweave.review.dto.ReviewDecisionRequest;
import com.veriweave.review.entity.FindingResponse;
import com.veriweave.review.repository.FindingResponseRepository;
import com.veriweave.review.service.ReviewService;
import com.veriweave.security.UserPrincipal;
import com.veriweave.security.WorkspaceSecurityService;
import com.veriweave.submission.dto.ContentVersionDto;
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
        workspaceSecurity.requireMembership(workspaceId, principal);
        Submission sub = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));

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
            @RequestBody String newRawText,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.MANAGER, Role.CREATOR);
        SubmissionDto dto = submissionService.createRevision(workspaceId, submissionId, newRawText, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Revision created", dto));
    }

    @GetMapping("/{submissionId}/versions")
    public ResponseEntity<ApiResponse<List<ContentVersionDto>>> listVersions(
            @PathVariable Long workspaceId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        workspaceSecurity.requireMembership(workspaceId, principal);
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
        workspaceSecurity.requireMembership(workspaceId, principal, Role.ADMIN, Role.MANAGER, Role.CREATOR);
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            Submission sub = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                    .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
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
        workspaceSecurity.requireMembership(workspaceId, principal);
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            Submission sub = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                    .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
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
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            Submission sub = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                    .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace: " + submissionId));
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
        workspaceSecurity.requireMembership(workspaceId, principal);
        List<ReviewDecisionDto> decisions = reviewService.getSubmissionDecisions(workspaceId, submissionId);
        return ResponseEntity.ok(ApiResponse.success(decisions));
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
