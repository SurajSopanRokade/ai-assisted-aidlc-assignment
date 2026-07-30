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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Domain logic for the shortener: code allocation, redirect resolution, click
 * accounting and analytics.
 *
 * <p>Deliberately free of HTTP concerns so it can be unit-tested without a web
 * context and reused from a non-HTTP entry point (batch import, gRPC) later.
 */
@Slf4j
@Service
public class ShortenerService {

    /**
     * Base62. The alphabet is fixed and URL-safe: no characters that need
     * percent-encoding, and none that change meaning in a path segment.
     */
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 7;

    /**
     * Bounded so a saturated keyspace fails fast and visibly (503) instead of
     * spinning. 62^7 is ~3.5e12 codes, so at prototype volume a collision is
     * already improbable; five attempts makes exhaustion a signal that
     * something is wrong, not a routine occurrence.
     */
    private static final int MAX_SHORT_CODE_GENERATION_ATTEMPTS = 5;

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    /** SecureRandom, not Random: short codes are guessable identifiers for
     *  otherwise-unlisted links, so the sequence must not be predictable. */
    private final SecureRandom random = new SecureRandom();

    private final Counter linksCreated;
    private final Counter redirectsResolved;
    private final Counter redirectsNotFound;
    private final Counter redirectsExpired;

    public ShortenerService(UrlRepository urlRepository,
                            ClickEventRepository clickEventRepository,
                            Clock clock,
                            MeterRegistry meterRegistry) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
        this.linksCreated = Counter.builder("urlshortener.links.created")
                .description("Short links created")
                .register(meterRegistry);
        this.redirectsResolved = Counter.builder("urlshortener.redirects")
                .tag("outcome", "resolved")
                .description("Redirect lookups by outcome")
                .register(meterRegistry);
        this.redirectsNotFound = Counter.builder("urlshortener.redirects")
                .tag("outcome", "not_found")
                .description("Redirect lookups by outcome")
                .register(meterRegistry);
        this.redirectsExpired = Counter.builder("urlshortener.redirects")
                .tag("outcome", "expired")
                .description("Redirect lookups by outcome")
                .register(meterRegistry);
    }

    /**
     * Allocates a short code and persists the mapping.
     *
     * @param customAlias caller-supplied code, or {@code null} to auto-generate
     * @param expiresAt   expiry instant, or {@code null} for a permanent link
     * @throws DuplicateAliasException       if the requested alias is taken
     * @throws ShortCodeGenerationException  if no free code was found in the retry budget
     */
    @Transactional
    public Url createShortUrl(String originalUrl, String customAlias, LocalDateTime expiresAt) {
        String code = (customAlias != null && !customAlias.isBlank())
                ? claimCustomAlias(customAlias)
                : allocateGeneratedCode();

        Url url = new Url();
        url.setOriginalUrl(originalUrl);
        url.setShortCode(code);
        url.setClickCount(0);
        url.setCreatedAt(LocalDateTime.now(clock));
        url.setExpiresAt(expiresAt);

        Url saved = urlRepository.save(url);
        linksCreated.increment();
        // The destination is caller-supplied and may contain credentials in a
        // query string, so it is not written to logs at INFO. The short code is
        // enough to correlate with the stored row.
        log.info("Created short code {}", code);
        return saved;
    }

    /**
     * Resolves a short code to its destination and records the click.
     *
     * <p>Returns the destination rather than the entity: the caller needs a
     * redirect target, and the entity's in-memory {@code clickCount} is stale
     * the moment the atomic increment runs.
     *
     * @throws ShortCodeNotFoundException if no such code exists
     * @throws LinkExpiredException       if the link exists but has expired
     */
    @Transactional
    public String resolveAndRecordClick(String shortCode) {
        Url record = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    redirectsNotFound.increment();
                    return new ShortCodeNotFoundException("No URL found for short code '" + shortCode + "'");
                });

        if (isExpired(record)) {
            redirectsExpired.increment();
            log.warn("Attempt to access expired short code: {}", shortCode);
            throw new LinkExpiredException("The short link '" + shortCode + "' has expired.");
        }

        ClickEvent event = new ClickEvent();
        event.setUrl(record);
        event.setClickedAt(LocalDateTime.now(clock));
        clickEventRepository.save(event);

        // Atomic in the database; see UrlRepository#incrementClickCount for why
        // an in-memory increment is unsafe here.
        urlRepository.incrementClickCount(record.getId());

        redirectsResolved.increment();
        return record.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode) {
        Url record = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("No URL found for short code '" + shortCode + "'"));

        LocalDateTime lastClickedAt = clickEventRepository.findLastClickedAt(record.getId()).orElse(null);

        return AnalyticsResponse.builder()
                .shortCode(record.getShortCode())
                .originalUrl(record.getOriginalUrl())
                .clickCount(record.getClickCount())
                .createdAt(record.getCreatedAt())
                .expiresAt(record.getExpiresAt())
                .expired(isExpired(record))
                .lastClickedAt(lastClickedAt)
                .build();
    }

    private boolean isExpired(Url record) {
        return record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now(clock));
    }

    /**
     * The existence check is an early, friendly rejection — not the uniqueness
     * guarantee. Two concurrent requests for the same alias can both pass it;
     * the unique index on {@code short_code} is what actually decides, and the
     * loser's {@code DataIntegrityViolationException} is translated to 409 in
     * {@link com.copilot.urlshortener.controller.GlobalExceptionHandler}.
     */
    private String claimCustomAlias(String customAlias) {
        if (urlRepository.existsByShortCode(customAlias)) {
            throw new DuplicateAliasException("Alias '" + customAlias + "' is already in use");
        }
        return customAlias;
    }

    private String allocateGeneratedCode() {
        for (int attempt = 0; attempt < MAX_SHORT_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateCandidateCode();
            if (!urlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.debug("Short code collision on attempt {}", attempt + 1);
        }
        throw new ShortCodeGenerationException(
                "Could not generate a unique short code after "
                        + MAX_SHORT_CODE_GENERATION_ATTEMPTS + " attempts");
    }

    private String generateCandidateCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
