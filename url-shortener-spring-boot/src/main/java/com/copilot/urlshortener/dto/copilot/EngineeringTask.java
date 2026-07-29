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
public class EngineeringTask {
    private String id;
    private String title;
    private String description;
    private List<String> depends_on;
    private String ai_assistance;
}
