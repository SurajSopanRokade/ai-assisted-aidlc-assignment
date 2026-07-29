package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.url.AnalyticsResponse;
import com.copilot.urlshortener.exception.DuplicateAliasException;
import com.copilot.urlshortener.exception.LinkExpiredException;
import com.copilot.urlshortener.exception.ShortCodeGenerationException;
import com.copilot.urlshortener.exception.ShortCodeNotFoundException;
import com.copilot.urlshortener.model.ClickEvent;
import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.repository.ClickEventRepository;
import com.copilot.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortenerService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 7;
    private static final int MAX_SHORT_CODE_GENERATION_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();

    private String generateCandidateCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Transactional
    public Url createShortUrl(String originalUrl, String customAlias, LocalDateTime expiresAt) {
        String code = null;

        if (customAlias != null && !customAlias.isEmpty()) {
            if (urlRepository.existsByShortCode(customAlias)) {
                throw new DuplicateAliasException("Alias '" + customAlias + "' is already in use");
            }
            code = customAlias;
        } else {
            for (int i = 0; i < MAX_SHORT_CODE_GENERATION_ATTEMPTS; i++) {
                String candidate = generateCandidateCode();
                if (!urlRepository.existsByShortCode(candidate)) {
                    code = candidate;
                    break;
                }
            }
            if (code == null) {
                throw new ShortCodeGenerationException("Could not generate a unique short code after " + MAX_SHORT_CODE_GENERATION_ATTEMPTS + " attempts");
            }
        }

        Url url = new Url();
        url.setOriginalUrl(originalUrl);
        url.setShortCode(code);
        url.setClickCount(0);
        url.setCreatedAt(LocalDateTime.now());
        url.setExpiresAt(expiresAt);
        
        log.info("Created short URL {} for original URL {}", code, originalUrl);
        return urlRepository.save(url);
    }

    @Transactional
    public Url resolveAndRecordClick(String shortCode) {
        Url record = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("No URL found for short code '" + shortCode + "'"));

        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to access expired short code: {}", shortCode);
            throw new LinkExpiredException("The short link '" + shortCode + "' has expired.");
        }

        record.setClickCount(record.getClickCount() + 1);
        
        ClickEvent event = new ClickEvent();
        event.setUrl(record);
        event.setClickedAt(LocalDateTime.now());
        clickEventRepository.save(event);
        
        return urlRepository.save(record);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode) {
        Url record = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("No URL found for short code '" + shortCode + "'"));

        LocalDateTime lastClickedAt = null;
        if (record.getClickEvents() != null && !record.getClickEvents().isEmpty()) {
            lastClickedAt = record.getClickEvents().stream()
                    .map(ClickEvent::getClickedAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }

        return AnalyticsResponse.builder()
                .shortCode(record.getShortCode())
                .originalUrl(record.getOriginalUrl())
                .clickCount(record.getClickCount())
                .createdAt(record.getCreatedAt())
                .lastClickedAt(lastClickedAt)
                .build();
    }
}
