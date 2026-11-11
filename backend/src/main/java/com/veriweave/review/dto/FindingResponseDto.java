package com.veriweave.review.dto;

import com.veriweave.review.entity.CreatorAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingResponseDto {
    private Long id;
    private Long findingId;
    private CreatorAction action;
    private String modifiedText;
    private String reason;
    private String respondedBy;
    private Instant createdAt;
}
