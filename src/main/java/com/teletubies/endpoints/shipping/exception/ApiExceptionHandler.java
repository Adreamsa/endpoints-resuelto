package com.teletubies.endpoints.shipping.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Translates every failure into the same ApiErrorResponse body.
 *
 * Status criteria:
 *   400 - the request could not be understood or a field is out of range (format).
 *   404 - the URL does not exist. Never used for business conditions.
 *   405 / 415 - the URL exists but the method or content type does not.
 *   422 - the request was understood and is well formed, but it breaks a business rule.
 *   500 - a defect on our side.
 *
 * Spring's own web exceptions are handled explicitly so they keep their real status
 * instead of being swallowed by the catch-all handler and reported as 500.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(final MethodArgumentNotValidException ex) {
        // Every failing field is reported at once; returning only the first one leaves the
        // client fixing its request one round trip at a time.
        final List<ApiErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiErrorResponse.FieldErrorDetail::field))
                .toList();

        log.warn("Request rejected, {} invalid field(s)", details.size());

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(final HttpMessageNotReadableException ex) {
        log.warn("Request rejected, unreadable body: {}", ex.getMostSpecificCause().getMessage());

        // The parser message is not echoed back: it leaks internal types and is unusable
        // by the client.
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON", List.of());
    }

    @ExceptionHandler(UnsupportedZoneException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedZone(final UnsupportedZoneException ex) {
        log.warn("Request rejected, unsupported zone '{}' on field '{}'",
                ex.getReceivedCode(), ex.getField());

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_ZONE", ex.getMessage(),
                List.of(new ApiErrorResponse.FieldErrorDetail(ex.getField(), ex.getMessage())));
    }

    @ExceptionHandler(WeightLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleWeightLimit(final WeightLimitExceededException ex) {
        log.warn("Request rejected, weight {} kg above limit {} kg",
                ex.getReceivedWeightKg(), ex.getMaxWeightKg());

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "WEIGHT_LIMIT_EXCEEDED", ex.getMessage(),
                List.of(new ApiErrorResponse.FieldErrorDetail("weightKg", ex.getMessage())));
    }

    @ExceptionHandler(InvalidRouteException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRoute(final InvalidRouteException ex) {
        log.warn("Request rejected, origin equals destination: '{}'", ex.getZoneCode());

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "ORIGIN_EQUALS_DESTINATION", ex.getMessage(),
                List.of(new ApiErrorResponse.FieldErrorDetail("destination", ex.getMessage())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(final NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "No endpoint matches " + ex.getHttpMethod() + " /" + ex.getResourcePath(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(final HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Method " + ex.getMethod() + " is not supported by this endpoint", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(final HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type " + ex.getContentType() + " is not supported, use application/json",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(final Exception ex) {
        // ERROR with the stack trace: this one is genuinely our fault and someone has to
        // be able to debug it. Client-side mistakes above stay at WARN and without a trace.
        log.error("Unexpected failure while handling the request", ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred, please try again later", List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(final HttpStatus status, final String code, final String message,
                                                   final List<ApiErrorResponse.FieldErrorDetail> details) {
        final ApiErrorResponse body = new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), code, message, details);
        return ResponseEntity.status(status).body(body);
    }
}
