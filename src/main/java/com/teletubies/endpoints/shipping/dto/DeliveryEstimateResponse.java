package com.teletubies.endpoints.shipping.dto;

/**
 * Delivery estimate expressed in business days. No calendar date is returned: that would
 * require a holiday calendar and a cutoff time, neither of which this service owns.
 */
public record DeliveryEstimateResponse(
        int businessDays,
        String description
) {
}
