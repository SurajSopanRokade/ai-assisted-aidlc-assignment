package com.copilot.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * The single error shape returned by every non-2xx response.
 *
 * <p>{@code detail} is always safe to display to an end user. Internal
 * information (exception types, SQL, stack traces) must never reach this
 * object; when a caller needs to report a failure they quote {@code error_id},
 * which correlates to the server-side log entry holding the real cause.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Human-readable, client-safe description of what went wrong. */
    private String detail;

    /**
     * Correlates this response with the server log entry that recorded the
     * underlying cause. Only populated for 5xx responses, where the detail is
     * intentionally generic.
     */
    @JsonProperty("error_id")
    private String errorId;

    /** Field name to message, populated for validation failures only. */
    @JsonProperty("field_errors")
    private Map<String, String> fieldErrors;

    public ErrorResponse(String detail) {
        this.detail = detail;
    }

    public static ErrorResponse of(String detail) {
        return new ErrorResponse(detail);
    }

    public static ErrorResponse withErrorId(String detail, String errorId) {
        return new ErrorResponse(detail, errorId, null);
    }

    public static ErrorResponse withFieldErrors(String detail, Map<String, String> fieldErrors) {
        return new ErrorResponse(detail, null, fieldErrors);
    }
}
