package com.veriweave.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.veriweave.review.entity.CreatorAction;
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
public class CreatorResponseRequest {

    @NotNull(message = "Action is required (ACCEPTED, MODIFIED, REJECTED)")
    private CreatorAction action;

    private String modifiedText;

    private String reason;

    @JsonSetter("proposedEdit")
    public void setProposedEdit(String proposedEdit) {
        if (this.modifiedText == null || this.modifiedText.isBlank()) {
            this.modifiedText = proposedEdit;
        }
    }

    @JsonSetter("rationale")
    public void setRationale(String rationale) {
        if (this.reason == null || this.reason.isBlank()) {
            this.reason = rationale;
        }
    }

    @JsonSetter("action")
    public void setActionFlexible(String actionStr) {
        if (actionStr != null) {
            String upper = actionStr.trim().toUpperCase();
            if ("ACCEPT".equals(upper)) upper = "ACCEPTED";
            else if ("MODIFY".equals(upper)) upper = "MODIFIED";
            else if ("REJECT".equals(upper)) upper = "REJECTED";
            this.action = CreatorAction.valueOf(upper);
        }
    }
}

