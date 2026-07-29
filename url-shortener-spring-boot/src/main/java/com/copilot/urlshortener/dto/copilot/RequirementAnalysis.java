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
public class RequirementAnalysis {
    private List<FunctionalRequirement> functional_requirements;
    private List<NonFunctionalRequirement> non_functional_requirements;
    private List<String> ambiguities;
    private List<String> assumptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionalRequirement {
        private String id;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NonFunctionalRequirement {
        private String category;
        private String description;
    }
}
