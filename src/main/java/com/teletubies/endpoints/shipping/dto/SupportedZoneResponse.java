package com.teletubies.endpoints.shipping.dto;

import java.math.BigDecimal;

/**
 * Outgoing payload of GET /api/v1/zones.
 *
 * The code is what the client must send back in a quote request; the display name is what
 * it shows in the selector. Rates are exposed so the UI can preview a zone without
 * round-tripping a quote.
 */
public record SupportedZoneResponse(
        String code,
        String displayName,
        String description,
        BigDecimal baseCost,
        BigDecimal costPerKg,
        int estimatedBusinessDays
) {
}
