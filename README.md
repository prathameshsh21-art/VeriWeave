# VeriWeave — Evidence-Linked Brand Governance

> **"The model proposes. Deterministic code verifies. A human decides."**

VeriWeave is an enterprise brand governance and copy compliance platform designed for high-stakes regulatory and brand-critical communication. It bridges the gap between probabilistic AI suggestions and deterministic regulatory enforcement by enforcing complete evidence traceability, separation of duties, and tamper-evident audit trails.

Developed for **CodeTitan India 2026 Round 3**.

---

## 1. Live Deployment & Repositories

| Component | Target URL / Repository |
| :--- | :--- |
| **Live Web Application** | [https://veriweave-frontend.onrender.com/](https://veriweave-frontend.onrender.com/) |
| **Backend REST API** | [https://veriweave-2c2q.onrender.com](https://veriweave-2c2q.onrender.com) |
| **GitHub Repository** | [https://github.com/prathameshsh21-art/VeriWeave](https://github.com/prathameshsh21-art/VeriWeave) |

---

## 2. Core Architecture & Differentiator

### The Guideline-to-Decision Traceability Chain
Traditional compliance tools either use simple regex dictionaries or ungrounded LLM outputs that hallucinate citations. VeriWeave implements a deterministic verification pipeline:

$$\text{Submitted Sentence} \longrightarrow \text{Finding} \longrightarrow \text{Supporting Evidence} \longrightarrow \text{Approved Guideline Version (SHA-256)} \longrightarrow \text{Suggested Correction} \longrightarrow \text{Creator Response} \longrightarrow \text{Reviewer Decision} \longrightarrow \text{Immutable Audit Record}$$

1. **Submitted Sentence**: User submits marketing copy or regulatory disclosure.
2. **Finding**: Identified potential violation with exact character start and end offsets.
3. **Supporting Evidence**: Exact verbatim excerpt extracted from an active knowledge document.
4. **Approved Guideline Version**: Backed by a cryptographic SHA-256 hash of an approved `CURRENT` guideline version.
5. **Suggested Correction**: Contextual fix compliant with the cited guideline.
6. **Creator Response**: Author accepts, modifies, or rejects with a mandatory rationale.
7. **Reviewer Decision**: Independent compliance officer issues an approval, rejection, or changes requested.
8. **Immutable Audit Record**: Tamper-evident chronological log stored in the database.

---

## 3. The Three Round 2 Commitments Delivered

### Commitment 1: Complete Knowledge Base System
- **Document Management**: Admins can upload PDFs and plaintext brand guidelines.
- **Content Extraction**: Text extraction via Apache PDFBox directly on upload.
- **Version Lifecycle**: Strict states: `DRAFT`, `CURRENT`, and `SUPERSEDED`.
- **Integrity**: Every source version stores a canonical SHA-256 content digest.
- **Approval Flow**: Only transactional approvals transition a draft to `CURRENT`, automatically superseding prior versions. Only `CURRENT` versions are evaluated during analysis.

### Commitment 2: Evidence-Based Analysis Engine
- **Deterministic Verification**: Every finding links to an exact passage in an approved guideline.
- **Character Offsets**: Precise `startOffset` and `endOffset` indexing the submitted text.
- **Zero Hallucination**: Described honestly as a rule-based analysis engine paired with deterministic code verification. If evidence cannot be matched deterministically to an active guideline version, the finding is rejected.
- **Actionable Remediation**: Each finding provides severity levels (`CRITICAL`, `WARNING`, `INFO`) and explicit correction recommendations directly derived from approved entries.

### Commitment 3: End-to-End Review Workflow & Separation of Duties
- **Role Isolation**: Strict server-side separation between `ADMIN`, `CREATOR`, and `REVIEWER`.
- **Separation of Duties Enforcement**: Compares the authenticated user's actual database ID against the submission author's ID. Self-approval attempts return HTTP 403 Forbidden.
- **Identity Note**: Separate accounts enforce distinct role boundaries and access controls in the database; in an evaluation environment, separate accounts simulate separate team roles rather than proving separate physical human identities.
- **Structured Review Flow**: Reviewers can approve, reject, or request changes with required justification. Critical overrides require explicit legal justification.
- **Audit Trails**: Full chronological ledger of every knowledge approval, submission check, response, and review decision.

---

## 4. Production Account & Role Management Hardening

### Server-Enforced Role Assignment
- Public registration (`/api/auth/register`) no longer accepts client-selected roles.
- Any newly registering user is initialized as `ADMIN` of their newly provisioned personal workspace.
- Team members are onboarded exclusively through **Workspace Team Invitations** managed by workspace Admins.

### Workspace Team Invitations
- Workspace Admins invite colleagues via email and select their role (`CREATOR` or `REVIEWER`).
- Secure cryptographic invitation tokens are generated (7-day validity).
- Existing users can accept an invitation to join the workspace immediately.
- New users follow a dedicated onboarding flow (`/invite/:token`) to set up their account and join.
- Admins can view pending invitations and revoke them at any time.

### Email Verification & Password Recovery
- **Verification Code**: 6-digit challenge code hashed with SHA-256, expiring in 15 minutes, with attempt throttling (max 5 attempts).
- **Forgot Password**: Secure email-initiated reset flow with token expiration.
- **Session Revocation**: `token_version` column on `users` table. Password resets increment the token version, immediately invalidating all outstanding JWT access and refresh tokens.
- **Honest SMTP Handling**: If SMTP environment variables are unconfigured, the system returns clear diagnostic feedback (`emailDelivery: false`, `providerStatus: "NOT READY FOR PRODUCTION"`) rather than falsely claiming delivery. In development/testing environments without SMTP, accounts are safely auto-activated.

### Authentication & Rate Limiting
- **JWT Secret Hardening**: Validates `VERIWEAVE_JWT_SECRET` on startup (`JwtService`). Weak or short keys (< 256 bits / 32 bytes) trigger a fatal initialization error.
- **CORS Protection**: Completely removed wildcard `*`. Restricted to explicit production origin (`https://veriweave-frontend.onrender.com`) and local development origins (`http://localhost:5173`, `http://127.0.0.1:5173`).
- **In-Memory Rate Limiting**: Sliding-window rate limiter protecting:
  - Login: 10 requests / minute
  - Registration: 5 requests / 10 minutes
  - Email verification & password reset: 5 requests / 10 minutes
  - Team invitations: 15 invitations / hour
  - Rate-limited responses return HTTP `429 Too Many Requests` with standard `Retry-After` header.

---

## 5. Technology Stack

### Backend
- **Language**: Java 17 (Adoptium / Eclipse Temurin)
- **Framework**: Spring Boot 3.2.5
- **Security**: Spring Security 6 with stateless JWT (Access + Refresh tokens)
- **Database Access**: Spring Data JPA / Hibernate
- **Migrations**: Flyway (`V1__baseline_schema.sql` through `V4__team_invitations_and_auth_hardening.sql`)
- **Document Processing**: Apache PDFBox 3.0.2
- **Testing**: JUnit 5, Mockito, MockMvc, H2 In-Memory Database

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite 5
- **Styling**: Tailwind CSS 3
- **Icons**: Lucide React
- **HTTP Client**: Axios with automatic JWT bearer token interceptor and 401 token refresh

---

## 6. Pre-Seeded Judge Demo Accounts

For immediate evaluation without manual onboarding, VeriWeave seeds a fictional enterprise tenant (**VeriFlora Botanicals**):

| Role | Email | Password | Pre-Verified | Server Permissions |
| :--- | :--- | :--- | :--- | :--- |
| **Admin** | `admin@veriflora.com` | `Secret123!` | Yes | Manage knowledge base, invite members, view audit trail |
| **Creator** | `creator@veriflora.com` | `Secret123!` | Yes | Submit copy, run evidence analysis, respond to findings |
| **Reviewer** | `reviewer@veriflora.com` | `Secret123!` | Yes | Review submissions, approve/reject, provide legal overrides |

> **Note**: One-click demo login buttons are available directly on the login screen.

---

## 7. Database Migrations (Flyway)

| Migration | Description |
| :--- | :--- |
| `V1__baseline_schema.sql` | Users, workspaces, memberships, knowledge sources, source versions, passages |
| `V2__submission_and_review.sql` | Submissions, submission versions, findings, creator responses, reviews |
| `V3__audit_and_hardening.sql` | Immutable audit log table and indexes |
| `V4__team_invitations_and_auth_hardening.sql` | Adds `email_verified` & `token_version` to `users`, creates `workspace_invitations` and `auth_verification_tokens` |

---

## 8. Verification & Test Suite

### Backend Integration Tests (16/16 Passing)
Execute all tests against H2 in-memory database:
```bash
cd backend
.\mvnw.cmd test
```

Test breakdown:
- `AuthIntegrationTest`: Registration, login, JWT issuance, refresh token rotation (4 tests).
- `EndToEndWorkflowIntegrationTest`: Complete 11-step governance workflow from guideline ingestion to creator response and reviewer signoff; separation of duties verification; workspace audit role access (4 tests).
- `TeamInvitationAndAuthHardeningTest`: Admin-only invitations, role restrictions, invite acceptance, session revocation via `token_version`, rate limiting HTTP 429 + `Retry-After`, and email service readiness (8 tests).

### Frontend Production Build
```bash
cd frontend
npm run build
```
Build output:
- `dist/index.html`
- `dist/assets/index-[hash].css`
- `dist/assets/index-[hash].js`
- 0 lint or build errors.

---

## 9. Environment Variables Configuration

### Backend (`application.yml` / Render Service)
| Variable | Required | Description |
| :--- | :--- | :--- |
| `VERIWEAVE_JWT_SECRET` | **Yes** | 256-bit+ secret key (min 32 bytes) for HMAC-SHA signing |
| `SPRING_DATASOURCE_URL` | **Yes** | JDBC URL (e.g. `jdbc:mysql://host:3306/veriweave?useSSL=true`) |
| `SPRING_DATASOURCE_USERNAME` | **Yes** | MySQL user |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | MySQL password |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed origins (default: production & localhost) |
| `SPRING_MAIL_HOST` | Optional | SMTP host (e.g. `smtp.sendgrid.net`) |
| `SPRING_MAIL_PORT` | Optional | SMTP port (e.g. `587`) |
| `SPRING_MAIL_USERNAME` | Optional | SMTP username |
| `SPRING_MAIL_PASSWORD` | Optional | SMTP password |
| `SPRING_MAIL_FROM` | Optional | Sender address (e.g. `noreply@veriweave.com`) |

### Frontend (`.env.production` / Render Static Site)
| Variable | Description |
| :--- | :--- |
| `VITE_API_URL` | `https://veriweave-2c2q.onrender.com/api` |

---

## 10. Manual Deployment & Rollout Checklist

When deploying to Render:
1. **Database**:
   - Ensure MySQL database is online.
   - Flyway migration `V4__team_invitations_and_auth_hardening.sql` will automatically apply on application startup.
2. **Backend Web Service**:
   - Verify `VERIWEAVE_JWT_SECRET` is set in Render environment variables (at least 32 characters / 256 bits).
   - Ensure `CORS_ALLOWED_ORIGINS` contains `https://veriweave-frontend.onrender.com`.
   - Deploy backend service and monitor logs for `Flyway Community Edition: Successfully applied migration V4`.
3. **Frontend Static Site**:
   - Verify `VITE_API_URL=https://veriweave-2c2q.onrender.com/api`.
   - Build command: `npm run build`, publish directory: `dist`.
   - Clear browser cache / CDN cache and verify login with `admin@veriflora.com`.
