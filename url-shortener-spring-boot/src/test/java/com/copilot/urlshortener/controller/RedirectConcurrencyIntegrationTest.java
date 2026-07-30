package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.dto.url.AnalyticsResponse;
import com.copilot.urlshortener.dto.url.ShortenResponse;
import com.copilot.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency regression test for the redirect path, run against a real
 * embedded container rather than MockMvc so the requests genuinely overlap.
 *
 * <p>Two independent failure modes converge on this one path, and neither is
 * visible at low load:
 *
 * <ul>
 *   <li><b>Failed requests.</b> Every redirect performs a write. Left with a
 *       default pool, multiple connections contend for SQLite's single writer
 *       lock and requests fail with
 *       {@code [SQLITE_BUSY] The database file is locked}. Held off by WAL
 *       journaling, a busy timeout, and a pool that queues writers rather than
 *       racing them.
 *   <li><b>Lost updates.</b> Incrementing the counter in memory via
 *       read-modify-write drops most of the count when requests overlap. Held
 *       off by an atomic SQL increment.
 * </ul>
 *
 * <p>The assertions below are exact: every request succeeds, and the count
 * equals the number of requests. An approximate assertion would let a lost
 * update slip through unnoticed — 80 of 100 is not obviously wrong if the test
 * only asks for "roughly right".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 100;
    private static final int THREADS = 16;
    private static final String DESTINATION = "https://example.com/concurrency-target";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UrlRepository urlRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
        executor = Executors.newFixedThreadPool(THREADS);

        // Follow no redirects: the test asserts on the 307 itself, and chasing
        // it would turn a unit of local behaviour into an outbound network call.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        restTemplate.getRestTemplate().setRequestFactory(factory);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("100 parallel redirects all succeed and are all counted")
    void concurrentRedirectsAreLosslessAndErrorFree() throws Exception {
        String shortCode = createLink();

        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            calls.add(() -> restTemplate.getForEntity("/" + shortCode, Void.class).getStatusCode().value());
        }

        List<Future<Integer>> futures = executor.invokeAll(calls, 60, TimeUnit.SECONDS);

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        Map<Integer, Long> byStatus = statuses.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        assertEquals(Map.of(HttpStatus.TEMPORARY_REDIRECT.value(), (long) CONCURRENT_REQUESTS), byStatus,
                "every concurrent redirect must succeed; a 500 here means the write path is contending on the database lock");

        ResponseEntity<AnalyticsResponse> analytics =
                restTemplate.getForEntity("/analytics/" + shortCode, AnalyticsResponse.class);

        assertEquals(HttpStatus.OK, analytics.getStatusCode());
        assertNotNull(analytics.getBody());
        assertEquals(CONCURRENT_REQUESTS, analytics.getBody().getClickCount(),
                "click count must equal the number of successful redirects; a lower value is a lost update");
        assertNotNull(analytics.getBody().getLastClickedAt());
    }

    @Test
    @DisplayName("parallel requests for the same custom alias produce exactly one winner")
    void concurrentAliasClaimsHaveOneWinner() throws Exception {
        String body = "{\"original_url\":\"" + DESTINATION + "\",\"custom_alias\":\"racealias\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> restTemplate
                    .postForEntity("/shorten", new HttpEntity<>(body, headers), String.class)
                    .getStatusCode().value());
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : executor.invokeAll(calls, 60, TimeUnit.SECONDS)) {
            statuses.add(future.get());
        }

        long created = statuses.stream().filter(s -> s == HttpStatus.CREATED.value()).count();
        long conflicts = statuses.stream().filter(s -> s == HttpStatus.CONFLICT.value()).count();

        assertEquals(1, created, "the unique index must admit exactly one claim for an alias");
        assertEquals(statuses.size() - 1, conflicts,
                "losers of the race must receive 409, never 500 — the integrity violation is a client-visible conflict");
        assertTrue(urlRepository.findByShortCode("racealias").isPresent());
    }

    private String createLink() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
                "/shorten",
                new HttpEntity<>("{\"original_url\":\"" + DESTINATION + "\"}", headers),
                ShortenResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().getShortCode();
    }
}
