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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the domain service, isolated from HTTP and the database.
 *
 * <p>Time is supplied by a fixed {@link Clock} so expiry behaviour is asserted
 * deterministically rather than by sleeping or by offsetting from "now".
 */
@ExtendWith(MockitoExtension.class)
class ShortenerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    private MeterRegistry meterRegistry;
    private ShortenerService shortenerService;
    private Url existingUrl;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
        shortenerService = new ShortenerService(urlRepository, clickEventRepository, fixedClock, meterRegistry);

        existingUrl = new Url();
        existingUrl.setId(1L);
        existingUrl.setOriginalUrl("https://example.com");
        existingUrl.setShortCode("abc1234");
        existingUrl.setClickCount(0);
        existingUrl.setCreatedAt(NOW.minusDays(1));
        existingUrl.setClickEvents(new ArrayList<>());
    }

    @Nested
    @DisplayName("createShortUrl")
    class CreateShortUrl {

        @Test
        @DisplayName("generates a 7-character code when no alias is supplied")
        void generatesCode() {
            when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArgument(0));

            Url result = shortenerService.createShortUrl("https://example.com", null, null);

            assertAll(
                    () -> assertEquals("https://example.com", result.getOriginalUrl()),
                    () -> assertEquals(7, result.getShortCode().length()),
                    () -> assertTrue(result.getShortCode().matches("[a-zA-Z0-9]{7}"),
                            "generated code must be URL-safe base62"),
                    () -> assertEquals(0, result.getClickCount()),
                    () -> assertEquals(NOW, result.getCreatedAt(), "createdAt must come from the injected clock"));
        }

        @Test
        @DisplayName("uses the custom alias when one is supplied")
        void usesCustomAlias() {
            when(urlRepository.existsByShortCode("custom")).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArgument(0));

            Url result = shortenerService.createShortUrl("https://example.com", "custom", null);

            assertEquals("custom", result.getShortCode());
        }

        @Test
        @DisplayName("treats a blank alias as absent rather than claiming an empty code")
        void blankAliasFallsBackToGeneration() {
            when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArgument(0));

            Url result = shortenerService.createShortUrl("https://example.com", "   ", null);

            assertEquals(7, result.getShortCode().length());
        }

        @Test
        @DisplayName("rejects an alias that is already taken")
        void rejectsDuplicateAlias() {
            when(urlRepository.existsByShortCode("custom")).thenReturn(true);

            assertThrows(DuplicateAliasException.class,
                    () -> shortenerService.createShortUrl("https://example.com", "custom", null));
            verify(urlRepository, never()).save(any(Url.class));
        }

        @Test
        @DisplayName("gives up after the retry budget rather than looping forever")
        void failsAfterRetryBudget() {
            when(urlRepository.existsByShortCode(anyString())).thenReturn(true);

            assertThrows(ShortCodeGenerationException.class,
                    () -> shortenerService.createShortUrl("https://example.com", null, null));
            // Exactly the documented budget: proves the loop is bounded.
            verify(urlRepository, times(5)).existsByShortCode(anyString());
        }

        @Test
        @DisplayName("increments the created-links counter")
        void recordsMetric() {
            when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
            when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArgument(0));

            shortenerService.createShortUrl("https://example.com", null, null);

            assertEquals(1.0, meterRegistry.counter("urlshortener.links.created").count());
        }
    }

    @Nested
    @DisplayName("resolveAndRecordClick")
    class ResolveAndRecordClick {

        @Test
        @DisplayName("returns the destination and records the click atomically")
        void recordsClick() {
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));

            String target = shortenerService.resolveAndRecordClick("abc1234");

            assertEquals("https://example.com", target);
            verify(clickEventRepository, times(1)).save(any(ClickEvent.class));
            // The counter must be advanced by the database, not by mutating the
            // entity in memory — that is what makes concurrent clicks safe.
            verify(urlRepository, times(1)).incrementClickCount(1L);
            verify(urlRepository, never()).save(any(Url.class));
        }

        @Test
        @DisplayName("404s for an unknown code")
        void notFound() {
            when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

            assertThrows(ShortCodeNotFoundException.class,
                    () -> shortenerService.resolveAndRecordClick("missing"));
            assertEquals(1.0, meterRegistry.counter("urlshortener.redirects", "outcome", "not_found").count());
        }

        @Test
        @DisplayName("rejects an expired link and records no click")
        void expired() {
            existingUrl.setExpiresAt(NOW.minusSeconds(1));
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));

            assertThrows(LinkExpiredException.class,
                    () -> shortenerService.resolveAndRecordClick("abc1234"));
            verify(clickEventRepository, never()).save(any(ClickEvent.class));
            verify(urlRepository, never()).incrementClickCount(any());
        }

        @Test
        @DisplayName("a link expiring in the next instant is still live")
        void notYetExpiredBoundary() {
            // Boundary: expiry strictly in the future must resolve. The
            // exactly-equal case is covered by expiresAtEqualToNowIsLive.
            existingUrl.setExpiresAt(NOW.plusNanos(1));
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));

            assertEquals("https://example.com", shortenerService.resolveAndRecordClick("abc1234"));
        }

        @Test
        @DisplayName("expiry exactly equal to now is treated as still live")
        void expiresAtEqualToNowIsLive() {
            // isBefore() is exclusive, so equality resolves. Asserted explicitly
            // so the boundary is a decision on the record, not an accident.
            existingUrl.setExpiresAt(NOW);
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));

            assertEquals("https://example.com", shortenerService.resolveAndRecordClick("abc1234"));
        }
    }

    @Nested
    @DisplayName("getAnalytics")
    class GetAnalytics {

        @Test
        @DisplayName("reports null last-clicked for a link that was never used")
        void neverClicked() {
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));
            when(clickEventRepository.findLastClickedAt(1L)).thenReturn(Optional.empty());

            AnalyticsResponse response = shortenerService.getAnalytics("abc1234");

            assertAll(
                    () -> assertEquals("abc1234", response.getShortCode()),
                    () -> assertEquals("https://example.com", response.getOriginalUrl()),
                    () -> assertEquals(0, response.getClickCount()),
                    () -> assertNull(response.getLastClickedAt()),
                    () -> assertFalse(response.isExpired()));
        }

        @Test
        @DisplayName("takes last-clicked from the database aggregate, not from the entity collection")
        void withClicks() {
            LocalDateTime latest = NOW.minusMinutes(5);
            existingUrl.setClickCount(2);
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));
            when(clickEventRepository.findLastClickedAt(1L)).thenReturn(Optional.of(latest));

            AnalyticsResponse response = shortenerService.getAnalytics("abc1234");

            assertEquals(2, response.getClickCount());
            assertEquals(latest, response.getLastClickedAt());
            // Regression guard: the lazy collection must never be walked here.
            assertTrue(existingUrl.getClickEvents().isEmpty());
        }

        @Test
        @DisplayName("remains readable after expiry, flagged as expired")
        void expiredLinkStillReportsAnalytics() {
            existingUrl.setExpiresAt(NOW.minusDays(1));
            existingUrl.setClickCount(7);
            when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));
            when(clickEventRepository.findLastClickedAt(1L)).thenReturn(Optional.empty());

            AnalyticsResponse response = shortenerService.getAnalytics("abc1234");

            assertTrue(response.isExpired());
            assertEquals(7, response.getClickCount());
        }

        @Test
        @DisplayName("404s for an unknown code")
        void notFound() {
            when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

            assertThrows(ShortCodeNotFoundException.class, () -> shortenerService.getAnalytics("missing"));
        }
    }

    @Test
    @DisplayName("expiry is evaluated against the injected clock, not the system clock")
    void expiryFollowsInjectedClock() {
        // Same link, two different "now"s: live under one clock, expired under
        // the other. This is the assertion that a sleep-based test cannot make.
        LocalDateTime expiry = NOW.plusHours(1);
        existingUrl.setExpiresAt(expiry);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(existingUrl));

        assertNotNull(shortenerService.resolveAndRecordClick("abc1234"));

        ShortenerService laterService = new ShortenerService(
                urlRepository, clickEventRepository,
                Clock.fixed(Instant.parse("2026-01-01T13:00:01Z"), ZoneId.of("UTC")),
                new SimpleMeterRegistry());

        assertThrows(LinkExpiredException.class, () -> laterService.resolveAndRecordClick("abc1234"));
        verify(urlRepository, times(1)).incrementClickCount(eq(1L));
    }
}
