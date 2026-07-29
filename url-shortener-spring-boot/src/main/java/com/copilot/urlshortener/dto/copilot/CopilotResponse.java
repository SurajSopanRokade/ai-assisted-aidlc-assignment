package com.copilot.urlshortener.dto.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotResponse {
    
    @JsonProperty("requirement_analysis")
    private RequirementAnalysis requirementAnalysis;
    
    @JsonProperty("task_decomposition")
    private List<EngineeringTask> taskDecomposition;
    
    @JsonProperty("engineering_artifacts")
    private EngineeringArtifacts engineeringArtifacts;
    
    private ValidationReport validation;
    
    @JsonProperty("risk_analysis")
    private RiskAnalysis riskAnalysis;
    
    @JsonProperty("final_summary")
    private FinalSummary finalSummary;
}
