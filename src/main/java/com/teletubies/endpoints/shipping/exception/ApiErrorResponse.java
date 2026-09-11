package com.teletubies.endpoints.shipping.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Single error contract for every endpoint of this API.
 *
 * `code` is the machine-readable discriminator the client branches on; `message` is the
 * human-readable text; `details` carries per-field problems and is omitted when empty, so
 * business errors are not forced to invent a field name.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        List<FieldErrorDetail> details
) {

    public record FieldErrorDetail(String field, String message) {
    }
}
