package com.teletubies.endpoints.shipping.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Incoming payload of POST /api/v1/quotes.
 *
 * Only format is validated here. Whether a zone exists, whether the route makes sense and
 * whether the weight is shippable are business rules and live in the service.
 *
 * Note there is no @Max on weightKg on purpose: the 70 kg ceiling is a business rule that
 * must answer 422 with an explanatory code, not a 400 field violation.
 */
public record QuoteRequest(

        @NotBlank(message = "Origin zone code is required")
        String origin,

        @NotBlank(message = "Destination zone code is required")
        String destination,

        @NotNull(message = "Package weight is required")
        @DecimalMin(value = "0.01", message = "Package weight must be greater than 0 kg")
        @Digits(integer = 3, fraction = 2,
                message = "Package weight accepts at most 3 integer digits and 2 decimals")
        BigDecimal weightKg
) {
}
