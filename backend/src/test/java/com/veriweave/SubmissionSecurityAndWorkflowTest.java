package com.veriweave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriweave.analysis.dto.AnalysisResponseDto;
import com.veriweave.auth.dto.LoginRequest;
import com.veriweave.auth.dto.RegisterRequest;
import com.veriweave.review.dto.CreatorResponseRequest;
import com.veriweave.review.dto.ReviewDecisionRequest;
import com.veriweave.review.entity.CreatorAction;
import com.veriweave.review.entity.ReviewDecisionType;
import com.veriweave.security.RateLimitingService;
import com.veriweave.submission.dto.CreateRevisionRequest;
import com.veriweave.submission.dto.CreateSubmissionRequest;
import com.veriweave.workspace.dto.AcceptInvitationRequest;
import com.veriweave.workspace.dto.CreateInvitationRequest;
import com.veriweave.workspace.entity.Role;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SubmissionSecurityAndWorkflowTest {

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
    void setUp() throws Exception {
        rateLimitingService.reset();

        adminToken = login("admin@veriflora.com", "Secret123!");
        creatorToken = login("creator@veriflora.com", "Secret123!");
        reviewerToken = login("reviewer@veriflora.com", "Secret123!");

        MvcResult meRes = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        workspaceId = objectMapper.readTree(meRes.getResponse().getContentAsString())
                .path("data").path("activeWorkspace").path("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = LoginRequest.builder().email(email).password(password).build();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private String createSecondCreator(String email, String name) throws Exception {
        CreateInvitationRequest invite = CreateInvitationRequest.builder()
                .email(email)
                .role(Role.CREATOR)
                .build();

        MvcResult inviteRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();

        AcceptInvitationRequest accept = AcceptInvitationRequest.builder()
                .fullName(name)
                .password("ComplexSecret123!")
                .build();

        MvcResult acceptRes = mockMvc.perform(post("/api/auth/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accept)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(acceptRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private record OutsiderInfo(String token, Long workspaceId) {}

    private OutsiderInfo createOutsider(String email, String orgName) throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .email(email)
                .password("ComplexSecret123!")
                .fullName("Outsider User")
                .workspaceName(orgName)
                .build();

        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        Long wsId = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("activeWorkspace").path("id").asLong();
        return new OutsiderInfo(token, wsId);
    }

    private record SubmissionInfo(Long id, Long versionId) {}

    private SubmissionInfo createSubmission(String token, String title, String rawText) throws Exception {
        CreateSubmissionRequest req = CreateSubmissionRequest.builder()
                .title(title)
                .rawText(rawText)
                .build();

        MvcResult res = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        Long versionId = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("currentVersion").path("id").asLong();
        return new SubmissionInfo(id, versionId);
    }

    private Long analyzeAndGetFindingId(String token, Long subId, Long versionId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + subId + "/versions/" + versionId + "/analyze")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("findings").get(0).path("id").asLong();
    }

    // ==========================================
    // Test 1: Creator Responding to Own Findings
    // ==========================================
    @Test
    @DisplayName("Test 1: Creator can respond to their own submission findings with valid actions (ACCEPT, MODIFY, REJECT)")
    void test1_creatorCanRespondToOwnSubmissionFindings() throws Exception {
        String copy = "Our organic serum is a cheap miracle cure for all aging signs, formulated with pure botanical extracts.";
        SubmissionInfo sub = createSubmission(creatorToken, "Serum Launch Copy", copy);
        Long findingId = analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        // 1. ACCEPT response
        CreatorResponseRequest acceptReq = CreatorResponseRequest.builder()
                .action(CreatorAction.ACCEPTED)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("ACCEPTED"));

        // 2. MODIFY response with modifiedText and reason
        CreatorResponseRequest modifyReq = CreatorResponseRequest.builder()
                .action(CreatorAction.MODIFIED)
                .modifiedText("affordable botanical formula")
                .reason("Replaced disfavored phrasing with brand compliant copy")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("MODIFIED"))
                .andExpect(jsonPath("$.data.modifiedText").value("affordable botanical formula"));

        // 3. REJECT response with required reason
        CreatorResponseRequest rejectReq = CreatorResponseRequest.builder()
                .action(CreatorAction.REJECTED)
                .reason("Valid under current regulatory exception memo")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("REJECTED"));

        // 4. REJECT response missing reason -> 400 Bad Request
        CreatorResponseRequest invalidReject = CreatorResponseRequest.builder()
                .action(CreatorAction.REJECTED)
                .reason("   ")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReject)))
                .andExpect(status().isBadRequest());
    }

    // ========================================================
    // Test 2: Creator Cannot Respond to Another Creator's Findings
    // ========================================================
    @Test
    @DisplayName("Test 2: Creator attempting to respond to another creator's findings is blocked with 403 Forbidden")
    void test2_creatorCannotRespondToAnotherCreatorsFindings() throws Exception {
        String creator2Token = createSecondCreator("other-creator@veriflora.com", "Second Creator");

        // Creator 1 creates submission & triggers analysis
        String copy = "Our organic serum is a cheap miracle cure for all aging signs.";
        SubmissionInfo sub1 = createSubmission(creatorToken, "Creator 1 Copy", copy);
        Long findingId = analyzeAndGetFindingId(creatorToken, sub1.id(), sub1.versionId());

        // Creator 2 attempts to respond to finding of Creator 1's submission
        CreatorResponseRequest req = CreatorResponseRequest.builder()
                .action(CreatorAction.ACCEPTED)
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creator2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Test 3: Creator Cannot Access Another Creator's Submission Artifacts
    // =====================================================================
    @Test
    @DisplayName("Test 3: Creator accessing another creator's submission detail / versions / findings / decisions / trace returns 403")
    void test3_creatorCannotAccessAnotherCreatorsSubmissionDetailsOrArtifacts() throws Exception {
        String creator2Token = createSecondCreator("isolated-creator@veriflora.com", "Isolated Creator");

        SubmissionInfo sub1 = createSubmission(creatorToken, "Confidential Creator 1 Copy", "A cheap miracle cure for fine lines.");
        Long findingId = analyzeAndGetFindingId(creatorToken, sub1.id(), sub1.versionId());

        // 1. Detail endpoint
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id())
                        .header("Authorization", "Bearer " + creator2Token))
                .andExpect(status().isForbidden());

        // 2. Versions endpoint
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/versions")
                        .header("Authorization", "Bearer " + creator2Token))
                .andExpect(status().isForbidden());

        // 3. Findings endpoint
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/findings")
                        .header("Authorization", "Bearer " + creator2Token))
                .andExpect(status().isForbidden());

        // 4. Decisions endpoint
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/decisions")
                        .header("Authorization", "Bearer " + creator2Token))
                .andExpect(status().isForbidden());

        // 5. Finding Traceability endpoint
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/findings/" + findingId + "/trace")
                        .header("Authorization", "Bearer " + creator2Token))
                .andExpect(status().isForbidden());

        // 6. Revision creation endpoint
        CreateRevisionRequest revReq = new CreateRevisionRequest();
        revReq.setRawText("Unauthorized revision attempt by another creator");
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub1.id() + "/revisions")
                        .header("Authorization", "Bearer " + creator2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isForbidden());
    }

    // ==================================================
    // Test 4: Prevent Cross-Workspace Submission Access
    // ==================================================
    @Test
    @DisplayName("Test 4: Cross-workspace submission access is strictly blocked with 403 Forbidden")
    void test4_preventCrossWorkspaceSubmissionAccess() throws Exception {
        OutsiderInfo outsider = createOutsider("outsider@external.com", "External Enterprise");
        SubmissionInfo sub = createSubmission(creatorToken, "VeriFlora Proprietary Copy", "Our gentle botanicals hydrate skin.");

        // Attempt to access VeriFlora's submission using the outsider token
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub.id())
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());

        // Attempt cross-workspace ID manipulation (using outsider workspaceId with VeriFlora's submissionId)
        mockMvc.perform(get("/api/workspaces/" + outsider.workspaceId() + "/submissions/" + sub.id())
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Test 5: Authorized Reviewer Can Access Eligible Submissions
    // ============================================================
    @Test
    @DisplayName("Test 5: Authorized reviewer can access eligible submissions and findings in their workspace")
    void test5_authorizedReviewerCanAccessEligibleSubmissions() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Reviewable Copy", "Our organic botanical serum is a cheap miracle cure.");
        analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub.id())
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sub.id()))
                .andExpect(jsonPath("$.data.title").value("Reviewable Copy"));

        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    // ============================================================
    // Test 6: Unauthorized User Cannot Access Submission Details
    // ============================================================
    @Test
    @DisplayName("Test 6: Unauthorized user without valid workspace credentials cannot access submission details")
    void test6_unauthorizedUserCannotAccessSubmissionDetails() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Guarded Submission", "Guarded copy.");

        // Unauthenticated request
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub.id()))
                .andExpect(status().isForbidden());
    }

    // ======================================================
    // Test 7: Reviewer Cannot Decide on DRAFT Submission
    // ======================================================
    @Test
    @DisplayName("Test 7: Reviewer attempting to decide on a DRAFT submission is rejected with 400 Bad Request")
    void test7_reviewerCannotDecideOnDraftSubmission() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Unanalyzed Draft", "Unanalyzed drafting text.");

        ReviewDecisionRequest decision = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Attempting premature approval without analysis")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("DRAFT")));
    }

    // ========================================================
    // Test 8: Reviewer Deciding on Valid IN_REVIEW Submission
    // ========================================================
    @Test
    @DisplayName("Test 8: Reviewer can record decision on valid IN_REVIEW submission")
    void test8_reviewerCanDecideOnValidInReviewSubmission() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Analyzed Copy", "A cheap miracle cure for dryness.");
        analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        ReviewDecisionRequest decision = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.CHANGES_REQUESTED)
                .comment("Please revise non-compliant miracle cure claim.")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("CHANGES_REQUESTED"));

        // Verify submission status changed to CHANGES_REQUESTED
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/submissions/" + sub.id())
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"));
    }

    // ==============================================================
    // Test 9: Reviewer Cannot Decide on Outdated Submission Version
    // ==============================================================
    @Test
    @DisplayName("Test 9: Reviewer attempting decision on an outdated version returns 400 Bad Request")
    void test9_reviewerCannotDecideOnOutdatedSubmissionVersion() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Outdated Version Test", "A cheap miracle cure.");
        analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        // Reviewer requests changes on v1
        ReviewDecisionRequest dec1 = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.CHANGES_REQUESTED)
                .comment("Need revision")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dec1)))
                .andExpect(status().isOk());

        // Creator creates v2 revision
        CreateRevisionRequest revReq = new CreateRevisionRequest();
        revReq.setRawText("Our organic serum is an affordable botanical formula.");
        MvcResult revRes = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/revisions")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long v2Id = objectMapper.readTree(revRes.getResponse().getContentAsString()).path("data").path("currentVersion").path("id").asLong();

        // Creator analyzes v2 -> transitions to IN_REVIEW
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/versions/" + v2Id + "/analyze")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());

        // Reviewer attempts to decide on superseded v1 -> 400 Bad Request
        ReviewDecisionRequest decOld = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Belated approval on outdated v1")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/versions/" + sub.versionId() + "/decide")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decOld)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("outdated")));
    }

    // ================================================================
    // Test 10: Reviewer Cannot Record Second Decision After APPROVED
    // ================================================================
    @Test
    @DisplayName("Test 10: Reviewer attempting a second decision after APPROVED returns 400 Bad Request")
    void test10_reviewerCannotRecordSecondDecisionAfterApproved() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Pre-Approved Copy", "Natural gentle botanical moisture for glowing skin.");
        // Run analysis -> no critical findings
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/versions/" + sub.versionId() + "/analyze")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());

        // Approve
        ReviewDecisionRequest approve = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Fully compliant copy")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        // Attempt second decision
        ReviewDecisionRequest secondDec = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.REJECTED)
                .comment("Duplicate post-approval decision")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondDec)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("APPROVED")));
    }

    // =================================================================
    // Test 11: Creator Cannot Make Reviewer Decisions (Separation of Duties)
    // =================================================================
    @Test
    @DisplayName("Test 11: Creator attempting to make review decisions is blocked with 403 Forbidden")
    void test11_creatorCannotMakeReviewerDecisionsEnforcingSeparationOfDuties() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Self-Approval Test", "A cheap miracle cure.");
        analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        ReviewDecisionRequest selfApprove = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Self-approval by author")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfApprove)))
                .andExpect(status().isForbidden());
    }

    // =======================================================================
    // Test 12: Critical Findings Require Audited Override Justification
    // =======================================================================
    @Test
    @DisplayName("Test 12: Critical findings require an audited override justification to approve")
    void test12_criticalFindingsRequireOverrideJustification() throws Exception {
        // "miracle cure" triggers a CRITICAL severity finding
        SubmissionInfo sub = createSubmission(creatorToken, "Critical Finding Copy", "Our serum is a miracle cure for all aging.");
        analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        // 1. Reviewer attempts approval without override reason -> 400 Bad Request
        ReviewDecisionRequest unoverriddenApprove = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Approving without justification")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unoverriddenApprove)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("override reason is required")));

        // 2. Reviewer provides audited override justification -> 200 OK
        ReviewDecisionRequest overriddenApprove = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.APPROVED)
                .comment("Approved per exception")
                .overrideReason("Approved by Legal Director under regulatory exemption 12-B")
                .build();

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overriddenApprove)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.overrideReason").value("Approved by Legal Director under regulatory exemption 12-B"));
    }

    // ============================================================
    // Test 13: Audit Events Recorded Correctly for Workflow Actions
    // ============================================================
    @Test
    @DisplayName("Test 13: Audit events are recorded accurately after valid workflow actions")
    void test13_auditEventsRecordedCorrectly() throws Exception {
        SubmissionInfo sub = createSubmission(creatorToken, "Audited Copy Flow", "A cheap miracle cure for dry skin.");
        Long findingId = analyzeAndGetFindingId(creatorToken, sub.id(), sub.versionId());

        // Creator responds
        CreatorResponseRequest resp = CreatorResponseRequest.builder()
                .action(CreatorAction.ACCEPTED)
                .build();
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/findings/" + findingId + "/respond")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resp)))
                .andExpect(status().isOk());

        // Reviewer decides
        ReviewDecisionRequest dec = ReviewDecisionRequest.builder()
                .decision(ReviewDecisionType.CHANGES_REQUESTED)
                .comment("Requested changes recorded in audit")
                .build();
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/submissions/" + sub.id() + "/decisions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dec)))
                .andExpect(status().isOk());

        // Admin checks audit trail
        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].action", hasItems(
                        "CREATE_SUBMISSION",
                        "RUN_ANALYSIS",
                        "RESPOND_TO_FINDING",
                        "REVIEW_DECISION"
                )));
    }

    // ==========================================================
    // Test 14: Creator Role Audit Isolation
    // ==========================================================
    @Test
    @DisplayName("Test 14: Creator role audit isolation ensures creators cannot see other creators' audit logs")
    void test14_creatorRoleAuditIsolation() throws Exception {
        String creator2Token = createSecondCreator("audit-creator2@veriflora.com", "Second Audit Creator");

        SubmissionInfo sub1 = createSubmission(creatorToken, "Creator 1 Submission", "Copy by Creator 1.");
        SubmissionInfo sub2 = createSubmission(creator2Token, "Creator 2 Submission", "Copy by Creator 2.");

        // Creator 1 requests workspace audit trail
        MvcResult creator1AuditRes = mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode creator1AuditTree = objectMapper.readTree(creator1AuditRes.getResponse().getContentAsString()).path("data");
        boolean creator1SeesSub1 = false;
        boolean creator1SeesSub2 = false;
        for (JsonNode event : creator1AuditTree) {
            if ("Submission".equalsIgnoreCase(event.path("entityType").asText())) {
                if (event.path("entityId").asLong() == sub1.id()) {
                    creator1SeesSub1 = true;
                }
                if (event.path("entityId").asLong() == sub2.id()) {
                    creator1SeesSub2 = true;
                }
            }
        }

        // Creator 1 must see sub1, but NOT sub2
        assertTrue(creator1SeesSub1, "Creator 1 audit should contain Creator 1's submission");
        assertFalse(creator1SeesSub2, "Creator 1 audit must NOT contain Creator 2's submission");

        // Admin requests workspace audit trail -> Sees both
        MvcResult adminAuditRes = mockMvc.perform(get("/api/workspaces/" + workspaceId + "/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode adminAuditTree = objectMapper.readTree(adminAuditRes.getResponse().getContentAsString()).path("data");
        boolean adminSeesSub1 = false;
        boolean adminSeesSub2 = false;
        for (JsonNode event : adminAuditTree) {
            if ("Submission".equalsIgnoreCase(event.path("entityType").asText())) {
                if (event.path("entityId").asLong() == sub1.id()) {
                    adminSeesSub1 = true;
                }
                if (event.path("entityId").asLong() == sub2.id()) {
                    adminSeesSub2 = true;
                }
            }
        }
        assertTrue(adminSeesSub1, "Admin audit should contain Creator 1's submission");
        assertTrue(adminSeesSub2, "Admin audit should contain Creator 2's submission");
    }
}
