package com.copilot.urlshortener.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalSummary {
    private String implementation_approach;
    private List<String> generated_artifacts;
    private String risks_and_validation;
    private String assumptions_and_limitations;
}
