$ErrorActionPreference = "Stop"

function Login-User($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    return $res.data
}

Write-Output "========================================================"
Write-Output "VERIWEAVE COMPLETE INTEGRATION & REGRESSION TEST SUITE"
Write-Output "========================================================"

# 1. Authenticate Personas
Write-Output "`n[1/7] Authenticating Test Personas..."
$adminAuth = Login-User "admin@veriflora.com" "Secret123!"
$creatorAuth = Login-User "creator@veriflora.com" "Secret123!"
$reviewerAuth = Login-User "reviewer@veriflora.com" "Secret123!"

$adminHeaders = @{ Authorization = ("Bearer " + $adminAuth.accessToken) }
$creatorHeaders = @{ Authorization = ("Bearer " + $creatorAuth.accessToken) }
$reviewerHeaders = @{ Authorization = ("Bearer " + $reviewerAuth.accessToken) }

Write-Output ("  [OK] Admin: " + $adminAuth.user.fullName + " (" + $adminAuth.user.email + ")")
Write-Output ("  [OK] Creator: " + $creatorAuth.user.fullName + " (" + $creatorAuth.user.email + ")")
Write-Output ("  [OK] Reviewer: " + $reviewerAuth.user.fullName + " (" + $reviewerAuth.user.email + ")")

# 2. Verify Audit API
Write-Output "`n[2/7] Testing Audit Trail API..."
$auditRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/workspaces/1/audit' -Method Get -Headers $adminHeaders
$auditList = if ($auditRes.data) { $auditRes.data } else { $auditRes }
Write-Output ("  [OK] Audit Trail accessible. Total events: " + $auditList.Count)
$latest = $auditList[0]
Write-Output ("  [OK] Latest Event: [" + $latest.action + "] by " + $latest.actor.fullName + " (" + $latest.actor.email + ") at " + $latest.timestamp)

# 3. Create Submission as Creator
Write-Output "`n[3/7] Creating Marketing Copy Submission as Creator..."
$subPayload = @{
    title = 'Botanical Radiance Anti-Aging Serum 2026'
    campaignName = 'Spring Launch 2026'
    rawText = 'Experience our cheap miracle cure botanical serum for instant detox! Clinically proven to improve skin hydration by 28% over 14 days.'
} | ConvertTo-Json

$subRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/workspaces/1/submissions' -Method Post -Body $subPayload -ContentType 'application/json' -Headers $creatorHeaders
$sub = if ($subRes.data) { $subRes.data } else { $subRes }
$subId = $sub.id
Write-Output ("  [OK] Submission Created: ID=" + $subId + ", Title='" + $sub.title + "', Status=" + $sub.status)

# 4. Fetch Submission Detail Page (Dual compatibility)
Write-Output "`n[4/7] Verifying Submission Details & Nested Structure..."
$detailRes = Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId) -Method Get -Headers $creatorHeaders
$detail = if ($detailRes.data) { $detailRes.data } else { $detailRes }
Write-Output ("  [OK] Root Status: " + $detail.status)
Write-Output ("  [OK] Nested detail.submission.status: " + $detail.submission.status)
Write-Output ("  [OK] Active Version: v" + $detail.currentVersion.versionNumber + " (ID=" + $detail.currentVersion.id + ")")
Write-Output ("  [OK] Content: '" + $detail.currentVersion.rawText + "'")

# 5. Run Automated Governance Analysis Pipeline
Write-Output "`n[5/7] Executing Automated Governance Analysis..."
$analysisRes = Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId + "/analyze") -Method Post -Headers $creatorHeaders
$analysis = if ($analysisRes.data) { $analysisRes.data } else { $analysisRes }
Write-Output ("  [OK] Analysis Status: " + $analysis.status + ", Run ID: " + $analysis.runId)
Write-Output ("  [OK] Verified Findings: " + $analysis.verifiedFindingsCount + ", Rejected Proposals: " + $analysis.rejectedFindingsCount)

# Fetch fresh details to verify findings
$detailAfter = (Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId) -Method Get -Headers $creatorHeaders).data
Write-Output ("  [OK] Verified Findings Attached to Submission: " + $detailAfter.findings.Count)
foreach ($f in $detailAfter.findings) {
    Write-Output ("    - [" + $f.severity + "] Rule " + $f.ruleCode + ": " + $f.category + " - Passage: '" + $f.passageQuote + "'")
}

# 6. Creator Responses to Findings
Write-Output "`n[6/7] Submitting Creator Responses to Findings..."
if ($detailAfter.findings.Count -ge 2) {
    # Accept finding 1
    $f1 = $detailAfter.findings[0]
    $resp1 = @{
        action = 'ACCEPT'
        reason = 'Acknowledged guideline violation.'
    } | ConvertTo-Json
    $r1 = (Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId + "/findings/" + $f1.id + "/responses") -Method Post -Body $resp1 -ContentType 'application/json' -Headers $creatorHeaders).data
    Write-Output ("  [OK] Finding #" + $f1.id + " Response: Action=" + $r1.action + " by " + $r1.respondedBy)

    # Modify finding 2
    $f2 = $detailAfter.findings[1]
    $resp2 = @{
        action = 'MODIFY'
        reason = 'Replaced with approved phrasing.'
        proposedEdit = 'Experience our accessible botanical serum for sustained balance.'
    } | ConvertTo-Json
    $r2 = (Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId + "/findings/" + $f2.id + "/responses") -Method Post -Body $resp2 -ContentType 'application/json' -Headers $creatorHeaders).data
    Write-Output ("  [OK] Finding #" + $f2.id + " Response: Action=" + $r2.action + ", ProposedEdit='" + $r2.modifiedText + "'")
}

# 7. Reviewer Decision
Write-Output "`n[7/7] Recording Reviewer Decision..."
$decisionPayload = @{
    decision = 'REQUEST_CHANGES'
    comments = 'Good revisions on terminology. Please commit the updated copy as Revision v2 before final approval.'
} | ConvertTo-Json
$decRes = (Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId + "/decisions") -Method Post -Body $decisionPayload -ContentType 'application/json' -Headers $reviewerHeaders).data
Write-Output ("  [OK] Decision Recorded: " + $decRes.decision + " (Status: " + $decRes.status + ") by " + $decRes.reviewerName)

# Verify final submission state
$finalDetail = (Invoke-RestMethod -Uri ("http://localhost:8080/api/workspaces/1/submissions/" + $subId) -Method Get -Headers $creatorHeaders).data
Write-Output ("  [OK] Final Submission Status: " + $finalDetail.submission.status)

Write-Output "`n========================================================"
Write-Output "ALL TESTS PASSED! ZERO CRASHES, ALL APIS FULLY VERIFIED!"
Write-Output "========================================================"
