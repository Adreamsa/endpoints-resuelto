package com.teletubies.endpoints.shipping.dto;

import java.math.BigDecimal;

/**
 * Outgoing payload of POST /api/v1/quotes.
 *
 * The request echo (origin, destination, weightKg) is returned normalized, so the client
 * can confirm what the server actually understood.
 */
public record QuoteResponse(
        String origin,
        String destination,
        BigDecimal weightKg,
        String currency,
        boolean overweight,
        CostBreakdownResponse costBreakdown,
        BigDecimal totalCost,
        DeliveryEstimateResponse delivery
) {
}
