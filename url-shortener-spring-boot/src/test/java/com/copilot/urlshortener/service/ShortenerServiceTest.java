package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.url.AnalyticsResponse;
import com.copilot.urlshortener.exception.DuplicateAliasException;
import com.copilot.urlshortener.exception.ShortCodeGenerationException;
import com.copilot.urlshortener.exception.ShortCodeNotFoundException;
import com.copilot.urlshortener.model.ClickEvent;
import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.repository.ClickEventRepository;
import com.copilot.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShortenerServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @InjectMocks
    private ShortenerService shortenerService;

    private Url mockUrl;

    @BeforeEach
    void setUp() {
        mockUrl = new Url();
        mockUrl.setId(1L);
        mockUrl.setOriginalUrl("https://example.com");
        mockUrl.setShortCode("abc1234");
        mockUrl.setClickCount(0);
        mockUrl.setCreatedAt(LocalDateTime.now());
        mockUrl.setClickEvents(new ArrayList<>());
    }

    @Test
    void testCreateShortUrl_AutoGenerate() {
        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArguments()[0]);

        Url result = shortenerService.createShortUrl("https://example.com", null, null);

        assertNotNull(result);
        assertEquals("https://example.com", result.getOriginalUrl());
        assertNotNull(result.getShortCode());
        assertEquals(7, result.getShortCode().length());
        assertEquals(0, result.getClickCount());
    }

    @Test
    void testCreateShortUrl_CustomAlias() {
        when(urlRepository.existsByShortCode("custom")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArguments()[0]);

        Url result = shortenerService.createShortUrl("https://example.com", "custom", null);

        assertNotNull(result);
        assertEquals("custom", result.getShortCode());
    }

    @Test
    void testCreateShortUrl_CustomAliasDuplicate() {
        when(urlRepository.existsByShortCode("custom")).thenReturn(true);

        assertThrows(DuplicateAliasException.class, () -> {
            shortenerService.createShortUrl("https://example.com", "custom", null);
        });
    }

    @Test
    void testCreateShortUrl_GenerationExhaustion() {
        when(urlRepository.existsByShortCode(anyString())).thenReturn(true);

        assertThrows(ShortCodeGenerationException.class, () -> {
            shortenerService.createShortUrl("https://example.com", null, null);
        });
    }

    @Test
    void testResolveAndRecordClick() {
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(mockUrl));
        when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArguments()[0]);

        Url result = shortenerService.resolveAndRecordClick("abc1234");

        assertEquals(1, result.getClickCount());
        verify(clickEventRepository, times(1)).save(any(ClickEvent.class));
    }

    @Test
    void testResolveAndRecordClick_NotFound() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortCodeNotFoundException.class, () -> {
            shortenerService.resolveAndRecordClick("missing");
        });
    }
    
    @Test
    void testResolveAndRecordClick_Expired() {
        mockUrl.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(mockUrl));

        assertThrows(com.copilot.urlshortener.exception.LinkExpiredException.class, () -> {
            shortenerService.resolveAndRecordClick("abc1234");
        });
    }

    @Test
    void testGetAnalytics_NeverClicked() {
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(mockUrl));

        AnalyticsResponse response = shortenerService.getAnalytics("abc1234");

        assertEquals("abc1234", response.getShortCode());
        assertEquals("https://example.com", response.getOriginalUrl());
        assertEquals(0, response.getClickCount());
        assertNull(response.getLastClickedAt());
    }

    @Test
    void testGetAnalytics_WithClicks() {
        mockUrl.setClickCount(2);
        
        ClickEvent event1 = new ClickEvent();
        event1.setClickedAt(LocalDateTime.now().minusDays(1));
        
        ClickEvent event2 = new ClickEvent();
        LocalDateTime latest = LocalDateTime.now();
        event2.setClickedAt(latest);
        
        mockUrl.setClickEvents(List.of(event1, event2));

        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(mockUrl));

        AnalyticsResponse response = shortenerService.getAnalytics("abc1234");

        assertEquals(2, response.getClickCount());
        assertEquals(latest, response.getLastClickedAt());
    }
}
