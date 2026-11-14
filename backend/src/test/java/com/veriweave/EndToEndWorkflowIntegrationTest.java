package com.veriweave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriweave.analysis.dto.FindingDto;
import com.veriweave.auth.dto.LoginRequest;
import com.veriweave.knowledge.dto.CreateEntryRequest;
import com.veriweave.knowledge.dto.CreateKnowledgeSourceRequest;
import com.veriweave.knowledge.entity.EntryCategory;
import com.veriweave.knowledge.entity.Severity;
import com.veriweave.knowledge.entity.SourceType;
import com.veriweave.review.dto.CreatorResponseRequest;
import com.veriweave.review.dto.ReviewDecisionRequest;
import com.veriweave.review.entity.CreatorAction;
import com.veriweave.review.entity.ReviewDecisionType;
import com.veriweave.security.RateLimitingService;
import com.veriweave.submission.dto.CreateRevisionRequest;
import com.veriweave.submission.dto.CreateSubmissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EndToEndWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingService rateLimitingService;

    private String adminToken;
    private String creatorToken;
    private String reviewerToken;
    private Long workspaceId;

    @BeforeEach
    void setupTokens() throws Exception {
        rateLimitingService.reset();

        // Authenticate with seeded demo users
        adminToken = login("admin@veriflora.com", "Secret123!");
        creatorToken = login("creator@veriflora.com", "Secret123!");
        reviewerToken = login("reviewer@veriflora.com", "Secret123!");

        // Retrieve workspace ID for VeriFlora
        MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        workspaceId = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .path("data").path("activeWorkspace").path("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = LoginRequest.builder().email(email).password(password).build();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Complete Core Workflow: Submit -> Propose -> Verify -> Creator Respond -> Reviewer Decide with Separation of Duties")
    void shouldExecuteFullVeriWeaveWorkflowSuccessfully() throws Exception {
        // Step 1: Creator submits drafting marketing copy that contains "cheap" and "miracle cure"
        String rawCopy = "Our organic serum is a cheap miracle cure for all aging signs, formulated with pure botanical extracts.";
        CreateSubmissionRequest subReq = CreateSubmissionRequest.builder()
                .title("Spring 2026 Social Campaign")
                .rawText(rawCopy)
                .build();

        MvcResult subResult = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Spring 2026 Social Campaign"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        long submissionId = objectMapper.readTree(subResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        long versionId = objectMapper.readTree(subResult.getResponse().getContentAsString()).path("data").path("currentVersion").path("id").asLong();

        // Step 2: Trigger Analysis Pipeline (The Model Proposes, Deterministic Code Verifies)
        MvcResult analyzeResult = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/versions/" + versionId + "/analyze")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findings", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.findings[0].classification").value("VERIFIED"))
                .andReturn();

        String findingsJson = analyzeResult.getResponse().getContentAsString();
        long findingId = objectMapper.readTree(findingsJson).path("data").path("findings").get(0).path("id").asLong();

        // Step 3: Creator Responds to Finding (Modifies / Accepts)
        CreatorResponseRequest respReq = CreatorResponseRequest.builder()
                .action(CreatorAction.ACCEPTED)
                .reason("Acknowledged guideline constraint")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("ACCEPTED"));

        // Step 4: Separation of Duties Enforcement -> Creator CANNOT approve own submission!
        ReviewDecisionRequest selfApproval = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Self approval attempt")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/versions/" + versionId + "/decide")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfApproval)))
                .andExpect(status().isForbidden()); // 403 Forbidden: Creator cannot approve own submission

        // Step 5: Independent Reviewer Decides -> Records decision
        ReviewDecisionRequest reviewerDecision = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.CHANGES_REQUESTED)
                .comment("Please update the text to replace 'cheap' with 'accessible' before publication.")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/versions/" + versionId + "/decide")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewerDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.reviewerName").value("Sarah Jenkins (Compliance Reviewer)"));

        // Step 6: Verify Submission Status is updated to CHANGES_REQUESTED
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"));

        // Step 7: Signature Differentiator -> Guideline-to-Decision Traceability Provenance
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/findings/" + findingId + "/trace")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findingId").value(findingId))
                .andExpect(jsonPath("$.data.submissionTitle").value("Spring 2026 Social Campaign"))
                .andExpect(jsonPath("$.data.creatorAction").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.reviewerDecision").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.ruleCode").isNotEmpty())
                .andExpect(jsonPath("$.data.sourceTitle").isNotEmpty());

        // Step 8: Creator Creates Revision (v2) Resolving the Feedback
        CreateRevisionRequest revisionReq = CreateRevisionRequest.builder()
                .rawText("Our botanical skincare is accessible and crafted with natural extracts.")
                .build();

        MvcResult revisionResult = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/revisions")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revisionReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentVersion.versionNumber").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        long version2Id = objectMapper.readTree(revisionResult.getResponse().getContentAsString())
                .path("data").path("currentVersion").path("id").asLong();

        // Step 9: Re-Analyze Revision v2
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/versions/" + version2Id + "/analyze")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());

        // Step 10: Independent Reviewer Approves the Compliant Revision
        ReviewDecisionRequest approvalDecision = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("All guidelines satisfied. Approved for publication.")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + submissionId + "/versions/" + version2Id + "/decide")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        // Step 11: Verify Submission Final Status is APPROVED
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Knowledge Base: Only ADMIN can approve version and approved versions become immutable")
    void shouldEnforceAdminOnlyApprovalAndImmutability() throws Exception {
        // 1. Non-admin (CREATOR) cannot create knowledge source
        CreateKnowledgeSourceRequest sourceReq = CreateKnowledgeSourceRequest.builder()
                .title("Unauthorized Source")
                .type(SourceType.GUIDELINE)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/sources")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sourceReq)))
                .andExpect(status().isForbidden());

        // 2. Admin creates knowledge source
        CreateKnowledgeSourceRequest validSourceReq = CreateKnowledgeSourceRequest.builder()
                .title("Packaging & Environmental Claims 2026")
                .type(SourceType.CLAIMS_REGISTER)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/sources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSourceReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long sourceId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        long draftVerId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("currentVersion").path("id").asLong();

        // 3. Admin adds entry to draft
        CreateEntryRequest entryReq = CreateEntryRequest.builder()
                .code("ENV-01")
                .category(EntryCategory.CLAIM)
                .defaultSeverity(Severity.CRITICAL)
                .text("100% Post-Consumer Recycled packaging (FSC Certified).")
                .pageOrSectionReference("Section 1.1")
                .build();

        MvcResult entryRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/sources/" + sourceId + "/versions/" + draftVerId + "/entries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entryReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long entryId = objectMapper.readTree(entryRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 4. Admin approves draft -> becomes CURRENT with SHA-256
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/sources/" + sourceId + "/versions/" + draftVerId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CURRENT"))
                .andExpect(jsonPath("$.data.checksum").isNotEmpty());

        // 5. Attempting to modify entry in CURRENT version is REJECTED
        CreateEntryRequest updateReq = CreateEntryRequest.builder()
                .code("ENV-01")
                .category(EntryCategory.CLAIM)
                .defaultSeverity(Severity.CRITICAL)
                .text("Modified text attempt")
                .build();

        mockMvc.perform(put("/api/workspaces/" + workspaceId + "/sources/" + sourceId + "/versions/" + draftVerId + "/entries/" + entryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Approved versions are immutable")));
    }

    @Test
    @DisplayName("Separation of Duties: Admin or Reviewer who authored a submission cannot review their own copy")
    void shouldEnforceSeparationOfDutiesWhenAdminAuthorsSubmission() throws Exception {
        // 1. Admin creates a submission
        CreateSubmissionRequest adminSubReq = CreateSubmissionRequest.builder()
                .title("Admin Authored Campaign")
                .campaignName("Leadership Announcements")
                .rawText("Authentic herbal formulations crafted for exceptional value.")
                .build();

        MvcResult subRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminSubReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long adminSubId = objectMapper.readTree(subRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        long adminSubVerId = objectMapper.readTree(subRes.getResponse().getContentAsString()).path("data").path("currentVersion").path("id").asLong();

        // 2. Admin attempts to review / approve their own submission -> 403 Forbidden!
        ReviewDecisionRequest selfReview = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Self review attempt by admin author")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + adminSubId + "/versions/" + adminSubVerId + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfReview)))
                .andExpect(status().isForbidden());

        // 3. Independent Reviewer decides on the Admin-authored submission -> 200 OK!
        ReviewDecisionRequest independentReview = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Independent evaluation by compliance reviewer")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + adminSubId + "/versions/" + adminSubVerId + "/decide")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(independentReview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));
    }

    @Test
    @DisplayName("Audit Trail: ADMIN, CREATOR and REVIEWER can access workspace audit; non-members are blocked")
    void shouldVerifyWorkspaceAuditAccessByRoleAndBlockNonMembers() throws Exception {
        // 1. Admin accesses audit trail -> 200 OK
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 2. Creator accesses audit trail -> 200 OK
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 3. Reviewer accesses audit trail -> 200 OK
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 4. Request for non-existent or un-joined workspace (e.g. 99999) -> 403 Forbidden
        mockMvc.perform(get("/api/workspaces/99999/audit")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isForbidden());
    }
}
