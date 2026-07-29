package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.dto.url.AnalyticsResponse;
import com.copilot.urlshortener.dto.url.ShortenRequest;
import com.copilot.urlshortener.dto.url.ShortenResponse;
import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.service.ShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UrlController {

    private final ShortenerService shortenerService;

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenResponse shortenUrl(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        log.info("Received request to shorten URL: {}", request.getOriginalUrl());
        Url record = shortenerService.createShortUrl(request.getOriginalUrl(), request.getCustomAlias(), request.getExpiresAt());

        // Construct base URL from the incoming request to match python behavior
        String baseUrl = httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + httpRequest.getServerPort();
        
        return ShortenResponse.builder()
                .id(record.getId())
                .originalUrl(record.getOriginalUrl())
                .shortCode(record.getShortCode())
                .shortUrl(baseUrl + "/" + record.getShortCode())
                .createdAt(record.getCreatedAt())
                .expiresAt(record.getExpiresAt())
                .build();
    }

    @GetMapping("/analytics/{shortCode}")
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        log.info("Fetching analytics for short code: {}", shortCode);
        return shortenerService.getAnalytics(shortCode);
    }

    @GetMapping("/{shortCode}")
    public RedirectView redirectShortUrl(@PathVariable String shortCode) {
        log.info("Received redirect request for short code: {}", shortCode);
        Url record = shortenerService.resolveAndRecordClick(shortCode);
        
        RedirectView redirectView = new RedirectView();
        redirectView.setUrl(record.getOriginalUrl());
        redirectView.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
        return redirectView;
    }
}
