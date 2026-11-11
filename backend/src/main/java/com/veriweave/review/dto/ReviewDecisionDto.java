package com.veriweave.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.veriweave.review.entity.ReviewDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionDto {
    private Long id;
    private Long submissionId;
    private Long contentVersionId;
    private ReviewDecisionType decision;
    private String reviewerName;
    private String comment;
    private String overrideReason;
    private Instant createdAt;

    @JsonProperty("status")
    public String getStatus() {
        return decision != null ? decision.name() : null;
    }

    @JsonProperty("reviewer")
    public Map<String, Object> getReviewer() {
        return reviewerName != null ? Map.of("fullName", reviewerName) : Map.of("fullName", "Reviewer");
    }
}

