package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.dto.copilot.CopilotRequest;
import com.copilot.urlshortener.dto.copilot.CopilotResponse;
import com.copilot.urlshortener.service.CopilotEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotEngineService copilotEngineService;

    @PostMapping("/analyze")
    public CopilotResponse analyzeRequirement(@Valid @RequestBody CopilotRequest request) {
        log.info("Analyzing software engineering requirement");
        return copilotEngineService.runCopilot(request.getRequirement());
    }
}
