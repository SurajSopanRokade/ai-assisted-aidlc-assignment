package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.dto.ErrorResponse;
import com.copilot.urlshortener.exception.DuplicateAliasException;
import com.copilot.urlshortener.exception.LinkExpiredException;
import com.copilot.urlshortener.exception.RateLimitExceededException;
import com.copilot.urlshortener.exception.ShortCodeGenerationException;
import com.copilot.urlshortener.exception.ShortCodeNotFoundException;
import com.copilot.urlshortener.web.RequestIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translates exceptions into the {@link ErrorResponse} contract.
 *
 * <p>Two rules govern everything in this class:
 *
 * <ol>
 *   <li><b>A client mistake is never a 5xx.</b> Unparseable JSON, a bad date, or
 *       a wrong path type are 4xx. Letting them fall through to the catch-all
 *       inflates the server error rate and makes 5xx alerting meaningless.
 *   <li><b>No exception message reaches the client from the catch-all.</b>
 *       Echoing {@code ex.getMessage()} discloses driver internals and raw SQL
 *       — a failed insert carries the whole statement in its message.
 *       Unexpected failures return a fixed message plus an {@code error_id}
 *       that points at the log entry holding the real cause.
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- Domain errors: message is authored by us, so it is safe to return ---

    @ExceptionHandler(DuplicateAliasException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAlias(DuplicateAliasException ex) {
        log.warn("Duplicate alias rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ex.getMessage()));
    }

    /**
     * The database unique index is the real arbiter of short-code uniqueness.
     * When two requests race for the same alias, the application-level check
     * passes for both and the loser fails here — which is a 409, not a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation, most likely a short-code race: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("That short code was just taken. Please retry."));
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeNotFound(ShortCodeNotFoundException ex) {
        log.warn("Short code not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ErrorResponse> handleLinkExpired(LinkExpiredException ex) {
        log.warn("Expired link requested: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of("Too many requests. Please retry in "
                        + ex.getRetryAfterSeconds() + " seconds."));
    }

    /**
     * Exhausting the generation retry budget means the keyspace is saturated or
     * the database is misbehaving — a transient server-side condition, so 503
     * (retryable) rather than 500.
     */
    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeGeneration(ShortCodeGenerationException ex) {
        log.error("Short code generation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(ErrorResponse.of("Could not allocate a short code. Please retry."));
    }

    // --- Client input errors ------------------------------------------------

    /** Bean validation failures on {@code @Valid @RequestBody} arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Request validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.withFieldErrors("Validation failed", errors));
    }

    /** Bean validation failures on path/query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(lastPathNode(violation), violation.getMessage());
        }
        log.warn("Parameter validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.withFieldErrors("Validation failed", errors));
    }

    /**
     * Malformed or unparseable request body — syntactically invalid JSON, a
     * date that is not ISO-8601, a string where a number was expected.
     *
     * <p>Without this handler they fall through to the catch-all and return 500
     * with the Jackson error verbatim, e.g. {@code "Cannot deserialize value of
     * type `java.time.LocalDateTime` ... at index 0"} — disclosing internal
     * types and misreporting a caller error as a server fault. The cause is
     * logged; the client gets a stable, generic 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                "Malformed request body. Check that the JSON is valid and that date fields "
                        + "use ISO-8601 format (for example 2026-12-31T23:59:59)."));
    }

    /** A path or query parameter that cannot be converted to its target type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter type mismatch on '{}'", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Invalid value for parameter '" + ex.getName() + "'."));
    }

    /**
     * Static-resource misses (favicon.ico and friends). Without this they reach
     * the catch-all and are logged at ERROR with a stack trace, which buries
     * real failures.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("Not found"));
    }

    // --- Catch-all ----------------------------------------------------------

    /**
     * Anything unanticipated. The client gets a fixed string and a correlation
     * id; the operator gets the exception and stack trace in the logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String errorId = currentErrorId();
        log.error("Unhandled exception [error_id={}]: {}", errorId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.withErrorId(
                "An internal error occurred. Quote this error_id when reporting the problem.", errorId));
    }

    /**
     * Reuses the per-request id from {@link com.copilot.urlshortener.web.RequestIdFilter}
     * so the id the client is given matches every log line for that request.
     * Falls back to a fresh id if the filter did not run (for example a failure
     * raised inside another filter).
     */
    private String currentErrorId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
