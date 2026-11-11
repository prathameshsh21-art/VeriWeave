package com.veriweave.submission.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateSubmissionRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Content text is required")
    private String rawText;

    private String campaignName;

    @JsonSetter("text")
    public void setText(String text) {
        if (this.rawText == null || this.rawText.isBlank()) {
            this.rawText = text;
        }
    }
}

