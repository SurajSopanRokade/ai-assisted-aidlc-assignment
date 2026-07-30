package com.copilot.urlshortener.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The inbound {@code X-Request-Id} is echoed into log output, so it is
 * untrusted input on a path that reaches the logs. Unsanitised, a value
 * containing newlines lets a caller forge log entries (CWE-117) — which is
 * worse than it sounds here, because these logs are what an incident is
 * reconstructed from.
 *
 * <p>The sanitiser is a security control, so it gets the same treatment as any
 * other: asserted behaviour, not assumed behaviour.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("generates an id when the client sends none")
    void generatesWhenAbsent() throws Exception {
        MockHttpServletResponse response = invoke(null);

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    @DisplayName("generates an id when the client sends a blank one")
    void generatesWhenBlank() throws Exception {
        assertNotNull(invoke("   ").getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("honours a well-formed inbound id so a trace survives a gateway hop")
    void honoursCleanInboundId() throws Exception {
        assertEquals("edge-abc_123.4", invoke("edge-abc_123.4").getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("strips newlines that would let a caller forge log entries")
    void stripsLogInjection() throws Exception {
        String forged = "abc\n2026-01-01 ERROR [admin] Payment approved";

        String id = invoke(forged).getHeader(RequestIdFilter.HEADER);

        assertFalse(id.contains("\n"), "a newline in the id would create a fake log line");
        assertFalse(id.contains(" "), "spaces would let the forged line mimic the log format");
        assertTrue(id.startsWith("abc"));
    }

    @Test
    @DisplayName("falls back to a generated id when nothing survives sanitisation")
    void generatesWhenFullyStripped() throws Exception {
        String id = invoke("\n\r\t@@@").getHeader(RequestIdFilter.HEADER);

        // Must not be empty: downstream code and the error contract both assume
        // a non-blank correlation id is always present.
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    @DisplayName("truncates an over-long id so a caller cannot bloat every log line")
    void truncatesLongId() throws Exception {
        String id = invoke("x".repeat(500)).getHeader(RequestIdFilter.HEADER);

        assertEquals(64, id.length());
    }

    @Test
    @DisplayName("clears the MDC so a pooled thread cannot mislabel the next request")
    void clearsMdcAfterRequest() throws Exception {
        invoke("trace-1");

        assertNull(MDC.get(RequestIdFilter.MDC_KEY),
                "a leftover id would attribute the next request's logs to this one");
    }

    private MockHttpServletResponse invoke(String inboundHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (inboundHeader != null) {
            request.addHeader(RequestIdFilter.HEADER, inboundHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }
}
