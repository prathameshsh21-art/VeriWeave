package com.veriweave.workspace.entity;

/**
 * The four user roles defined in the VeriWeave Round 1 proposal:
 * - ADMIN: Workspace owner, manages members, approves source versions, audit logs.
 * - MANAGER: Marketing lead, submits content, comments, views status & summaries.
 * - CREATOR: Content author, submits content, responds to findings with reasons.
 * - REVIEWER: Authorised brand reviewer, approves/rejects/requests changes with recorded reasons.
 */
public enum Role {
    ADMIN,
    MANAGER,
    CREATOR,
    REVIEWER
}
