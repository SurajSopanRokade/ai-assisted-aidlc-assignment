package com.copilot.urlshortener.exception;

import lombok.Getter;

/** Raised when a client exceeds the configured write-endpoint quota. */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /** Seconds until the client's window resets, surfaced as {@code Retry-After}. */
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
