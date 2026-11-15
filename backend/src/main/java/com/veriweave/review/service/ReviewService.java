package com.veriweave.review.service;

import com.veriweave.analysis.entity.Finding;
import com.veriweave.analysis.entity.FindingClassification;
import com.veriweave.analysis.repository.FindingRepository;
import com.veriweave.audit.service.AuditService;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.review.dto.CreatorResponseRequest;
import com.veriweave.review.dto.FindingResponseDto;
import com.veriweave.review.dto.ReviewDecisionDto;
import com.veriweave.review.dto.ReviewDecisionRequest;
import com.veriweave.review.entity.CreatorAction;
import com.veriweave.review.entity.FindingResponse;
import com.veriweave.review.entity.ReviewDecision;
import com.veriweave.review.entity.ReviewDecisionType;
import com.veriweave.review.repository.FindingResponseRepository;
import com.veriweave.review.repository.ReviewDecisionRepository;
import com.veriweave.security.UserPrincipal;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final SubmissionRepository submissionRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final FindingRepository findingRepository;
    private final FindingResponseRepository findingResponseRepository;
    private final ReviewDecisionRepository reviewDecisionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public FindingResponseDto respondToFinding(
            Long workspaceId,
            Long submissionId,
            Long findingId,
            CreatorResponseRequest request,
            UserPrincipal principal
    ) {
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace"));

        // 1. Creator Ownership Enforcement: Only the creator who owns the submission can respond
        if (submission.getCreatedBy() == null || !submission.getCreatedBy().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Access denied: Only the creator who owns this submission can respond to its findings.");
        }

        Finding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + findingId));

        if (!finding.getContentVersion().getSubmission().getId().equals(submission.getId())) {
            throw new IllegalArgumentException("Finding does not belong to specified submission");
        }

        // 2. Outdated version check: Findings must belong to the active/current content version
        if (submission.getCurrentVersion() != null && !finding.getContentVersion().getId().equals(submission.getCurrentVersion().getId())) {
            throw new IllegalStateException("Cannot respond to findings on an outdated version. Responses must apply to the active version.");
        }

        // 3. State integrity check: Cannot respond to findings on an already APPROVED submission
        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new IllegalStateException("Cannot respond to findings on an APPROVED submission.");
        }

        // 4. Action validation: Reject requires reason, Modify requires modifiedText
        if (request.getAction() == CreatorAction.REJECTED) {
            if (request.getReason() == null || request.getReason().trim().isBlank()) {
                throw new IllegalArgumentException("A mandatory reason is required when rejecting a finding suggestion");
            }
        }
        if (request.getAction() == CreatorAction.MODIFIED) {
            if (request.getModifiedText() == null || request.getModifiedText().trim().isBlank()) {
                throw new IllegalArgumentException("Modified text is required when choosing to modify a finding");
            }
        }

        User user = userRepository.findById(principal.getId()).orElseThrow();

        Optional<FindingResponse> existing = findingResponseRepository.findByFindingId(findingId);
        FindingResponse response = existing.orElseGet(() -> FindingResponse.builder().finding(finding).build());

        response.setUser(user);
        response.setAction(request.getAction());
        response.setModifiedText(request.getModifiedText());
        response.setReason(request.getReason() != null ? request.getReason().trim() : null);
        response = findingResponseRepository.save(response);

        auditService.logEvent(submission.getWorkspace(), user, "RESPOND_TO_FINDING", "Finding", finding.getId(),
                "Action: " + request.getAction() + (request.getReason() != null ? " Reason: " + request.getReason() : ""));

        return toResponseDto(response);
    }

    @Transactional
    public ReviewDecisionDto recordReviewDecision(
            Long workspaceId,
            Long submissionId,
            Long contentVersionId,
            ReviewDecisionRequest request,
            UserPrincipal principal
    ) {
        Submission submission = submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace"));

        ContentVersion version = contentVersionRepository.findByIdAndSubmissionId(contentVersionId, submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Content version not found"));

        User reviewer = userRepository.findById(principal.getId()).orElseThrow();

        // 1. Separation of Duties Enforcement: Creator can NEVER approve own submission!
        if (submission.getCreatedBy().getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("Separation of duties violation: A creator cannot approve their own submission.");
        }

        // 2. Prevent decisions on outdated / superseded content versions
        ContentVersion currentVersion = submission.getCurrentVersion();
        if (currentVersion != null && !currentVersion.getId().equals(contentVersionId)) {
            throw new IllegalStateException(String.format(
                    "Review decisions can only be recorded on the current active version (v%d). Version v%d is outdated/superseded.",
                    currentVersion.getVersionNumber(), version.getVersionNumber()));
        }

        // 3. Prevent decisions on DRAFT submissions (must complete analysis first)
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new IllegalStateException("Cannot record review decision: Submission is in DRAFT status and must complete analysis before review.");
        }

        // 4. Prevent repeated decisions after reaching terminal states
        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new IllegalStateException("Submission has already reached terminal APPROVED status. Subsequent review decisions are not permitted.");
        }
        if (submission.getStatus() == SubmissionStatus.REJECTED) {
            throw new IllegalStateException("Submission has already reached terminal REJECTED status. A new revision must be submitted before review.");
        }
        if (submission.getStatus() == SubmissionStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Review decision has already been recorded (CHANGES_REQUESTED). A new revision must be submitted before review.");
        }
        if (reviewDecisionRepository.existsByContentVersionId(version.getId())) {
            throw new IllegalStateException("A review decision has already been recorded for this version.");
        }

        // 2. Overriding Critical Findings requires an explicit recorded reason
        if (request.getDecision() == ReviewDecisionType.APPROVED) {
            List<Finding> findings = findingRepository.findAllByContentVersionIdWithDetails(contentVersionId);
            boolean hasUnresolvedCritical = false;

            for (Finding f : findings) {
                if (f.getClassification() == FindingClassification.VERIFIED && f.getSeverity() == Severity.CRITICAL) {
                    Optional<FindingResponse> resp = findingResponseRepository.findByFindingId(f.getId());
                    // Unresolved if creator didn't accept or if creator rejected
                    if (resp.isEmpty() || resp.get().getAction() == CreatorAction.REJECTED) {
                        hasUnresolvedCritical = true;
                        break;
                    }
                }
            }

            if (hasUnresolvedCritical) {
                if (request.getOverrideReason() == null || request.getOverrideReason().trim().isBlank()) {
                    throw new IllegalArgumentException("An explicit override reason is required to approve content with unresolved Critical findings.");
                }
            }
        }

        // 3. Save ReviewDecision
        ReviewDecision decision = ReviewDecision.builder()
                .submission(submission)
                .contentVersion(version)
                .reviewer(reviewer)
                .decision(request.getDecision())
                .comment(request.getComment())
                .overrideReason(request.getOverrideReason())
                .build();
        decision = reviewDecisionRepository.save(decision);

        // 4. Update Submission Status
        SubmissionStatus newStatus;
        switch (request.getDecision()) {
            case APPROVED -> newStatus = SubmissionStatus.APPROVED;
            case REJECTED -> newStatus = SubmissionStatus.REJECTED;
            case CHANGES_REQUESTED -> newStatus = SubmissionStatus.CHANGES_REQUESTED;
            default -> newStatus = SubmissionStatus.IN_REVIEW;
        }
        submission.setStatus(newStatus);
        submissionRepository.save(submission);

        // 5. Append-only Audit Trail
        auditService.logEvent(submission.getWorkspace(), reviewer, "REVIEW_DECISION", "Submission", submission.getId(),
                String.format("Decision: %s on v%d. Reason: %s", request.getDecision(), version.getVersionNumber(), 
                        request.getOverrideReason() != null ? request.getOverrideReason() : request.getComment()));

        return toDecisionDto(decision);
    }

    @Transactional(readOnly = true)
    public List<ReviewDecisionDto> getSubmissionDecisions(Long workspaceId, Long submissionId) {
        submissionRepository.findByIdAndWorkspaceId(submissionId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("Submission not found in workspace"));
        return reviewDecisionRepository.findAllBySubmissionIdOrderByCreatedAtDesc(submissionId).stream()
                .map(this::toDecisionDto)
                .collect(Collectors.toList());
    }

    public FindingResponseDto toResponseDto(FindingResponse r) {
        return FindingResponseDto.builder()
                .id(r.getId())
                .findingId(r.getFinding() != null ? r.getFinding().getId() : null)
                .action(r.getAction())
                .modifiedText(r.getModifiedText())
                .reason(r.getReason())
                .respondedBy(r.getUser().getFullName())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private ReviewDecisionDto toDecisionDto(ReviewDecision d) {
        return ReviewDecisionDto.builder()
                .id(d.getId())
                .submissionId(d.getSubmission().getId())
                .contentVersionId(d.getContentVersion().getId())
                .decision(d.getDecision())
                .reviewerName(d.getReviewer().getFullName())
                .comment(d.getComment())
                .overrideReason(d.getOverrideReason())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
