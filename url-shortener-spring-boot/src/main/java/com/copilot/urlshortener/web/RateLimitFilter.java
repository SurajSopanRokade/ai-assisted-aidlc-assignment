package com.copilot.urlshortener.web;

import com.copilot.urlshortener.config.AppProperties;
import com.copilot.urlshortener.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limit on the write endpoint.
 *
 * <p>{@code POST /shorten} is unauthenticated and writes a row per call, so
 * without a quota a single client can inflate the database indefinitely and
 * exhaust the short-code keyspace. Reads and redirects are deliberately not
 * limited: the redirect path is the product, and throttling it would degrade
 * legitimate traffic for no security benefit.
 *
 * <p><b>Deployment constraint:</b> counters are held in memory, so the quota is
 * per instance. Behind N replicas the effective limit is N x the configured
 * value. This is an accepted trade-off for a single-instance prototype; a
 * multi-instance deployment needs a shared counter (Redis) or an edge-enforced
 * policy at the gateway. Documented in LIMITATIONS.md rather than left implicit.
 *
 * <p>A fixed window is used instead of a sliding window or token bucket because
 * it needs one counter per client and no background eviction thread. Its known
 * weakness is burst tolerance at a window boundary (up to 2x the limit across
 * two adjacent windows), which is acceptable for abuse control at this scale.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LIMITED_PATH = "/shorten";
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final AppProperties properties;
    private final Clock clock;

    /**
     * A filter runs outside the DispatcherServlet, so an exception thrown here
     * would bypass {@code @RestControllerAdvice} entirely and surface as the
     * container's default error page. Delegating to the resolver routes it back
     * through {@link com.copilot.urlshortener.controller.GlobalExceptionHandler}
     * so a 429 has the same body shape as every other error.
     */
    private final HandlerExceptionResolver exceptionResolver;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties,
                           Clock clock,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.properties = properties;
        this.clock = clock;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRateLimit().isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        int limit = properties.getRateLimit().getRequestsPerMinute();
        long currentWindow = clock.millis() / 60_000L;
        String client = clientKey(request);

        Window window = windows.compute(client, (key, existing) ->
                existing == null || existing.startedAt != currentWindow
                        ? new Window(currentWindow)
                        : existing);

        if (window.count.incrementAndGet() > limit) {
            long retryAfter = 60 - ((clock.millis() / 1000L) % 60);
            exceptionResolver.resolveException(request, response, null, new RateLimitExceededException(
                    "Rate limit of " + limit + " requests/minute exceeded", retryAfter));
            return;
        }

        evictIfOversized(currentWindow);
        chain.doFilter(request, response);
    }

    /**
     * Client identity for quota purposes.
     *
     * <p>Uses the socket address, never {@code X-Forwarded-For}: that header is
     * client-supplied and trusting it lets an attacker rotate a spoofed value
     * per request and bypass the limit entirely. Behind a load balancer this
     * requires {@code server.forward-headers-strategy=native} plus a trusted
     * proxy list so the container populates the remote address correctly.
     */
    private String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /**
     * Bounds memory used by the counter map. Without this, a source cycling
     * through addresses turns the limiter itself into a memory-exhaustion
     * vector. Entries from an elapsed window are dead weight, so they go first.
     */
    private void evictIfOversized(long currentWindow) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt != currentWindow);
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            log.warn("Rate limiter tracking {} clients within a single window; consider a shared store",
                    windows.size());
            windows.clear();
        }
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
