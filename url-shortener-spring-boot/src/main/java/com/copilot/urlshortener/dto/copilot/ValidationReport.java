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
public class ValidationReport {
    private List<ValidationFinding> code_review;
    private List<ValidationFinding> security_review;
    private List<ValidationFinding> performance_review;
    private List<String> missing_edge_cases;
    private String test_coverage_summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationFinding {
        private String area;
        private String finding;
        private String severity;
    }
}
