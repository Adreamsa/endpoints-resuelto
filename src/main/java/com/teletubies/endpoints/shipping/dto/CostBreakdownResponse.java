package com.teletubies.endpoints.shipping.dto;

import java.math.BigDecimal;

/**
 * Itemized cost so the client can display how the total was reached instead of
 * re-implementing the arithmetic and landing on a different number.
 */
public record CostBreakdownResponse(
        BigDecimal baseCost,
        BigDecimal weightCost,
        BigDecimal subtotal,
        BigDecimal overweightSurcharge
) {
}
