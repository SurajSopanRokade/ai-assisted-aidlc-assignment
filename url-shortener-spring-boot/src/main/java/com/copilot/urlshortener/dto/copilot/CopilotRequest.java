package com.copilot.urlshortener.dto.copilot;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CopilotRequest {
    @Size(min = 10, message = "requirement must be at least 10 characters long")
    private String requirement;
}
