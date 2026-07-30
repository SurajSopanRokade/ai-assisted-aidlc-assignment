package com.copilot.urlshortener.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns every request a correlation id, exposes it to the logging framework
 * through the MDC, and echoes it back as {@code X-Request-Id}.
 *
 * <p>This is what makes a support report actionable: a 500 response carries an
 * {@code error_id}, and that same value appears on every log line the request
 * produced, so the real cause can be found without asking the user to reproduce.
 *
 * <p>An inbound {@code X-Request-Id} is honoured so a trace survives across an
 * upstream gateway, but it is sanitised first — the value lands in log output,
 * and unvalidated input in logs enables log injection and forgery (CWE-117).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    private static final int MAX_INBOUND_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = sanitiseInbound(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused; leaving the id set would mislabel
            // the next request handled by this thread.
            MDC.remove(MDC_KEY);
        }
    }

    private String sanitiseInbound(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = candidate.replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > MAX_INBOUND_LENGTH ? cleaned.substring(0, MAX_INBOUND_LENGTH) : cleaned;
    }
}
