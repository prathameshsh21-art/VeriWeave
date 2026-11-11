package com.veriweave.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.veriweave.review.entity.ReviewDecisionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewDecisionRequest {

    @NotNull(message = "Decision is required (APPROVED, REJECTED, CHANGES_REQUESTED)")
    private ReviewDecisionType decision;

    private String comment;

    private String overrideReason;

    @JsonSetter("comments")
    public void setComments(String comments) {
        if (this.comment == null || this.comment.isBlank()) {
            this.comment = comments;
        }
    }

    @JsonSetter("notes")
    public void setNotes(String notes) {
        if (this.comment == null || this.comment.isBlank()) {
            this.comment = notes;
        }
    }

    @JsonSetter("decision")
    public void setDecisionFlexible(String decisionStr) {
        if (decisionStr != null) {
            String upper = decisionStr.trim().toUpperCase();
            if ("APPROVE".equals(upper)) upper = "APPROVED";
            else if ("REQUEST_CHANGES".equals(upper)) upper = "CHANGES_REQUESTED";
            else if ("REJECT".equals(upper)) upper = "REJECTED";
            this.decision = ReviewDecisionType.valueOf(upper);
        }
    }
}

