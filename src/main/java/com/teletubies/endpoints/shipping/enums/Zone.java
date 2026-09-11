package com.teletubies.endpoints.shipping.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Supported shipping zones and their rate table.
 *
 * The catalog lives in one place so the GET endpoint and the POST validation can never
 * disagree. This is a compile-time constant, not persistence or cross-request state.
 */
@Getter
public enum Zone {

    METROPOLITAN(
            "Metropolitan area",
            "Same-city deliveries inside the metropolitan area.",
            new BigDecimal("90.00"), new BigDecimal("12.00"), 1),

    NATIONAL_CENTRAL(
            "Central region",
            "Domestic deliveries to states in the central region.",
            new BigDecimal("140.00"), new BigDecimal("18.00"), 2),

    NATIONAL_EXTENDED(
            "Extended domestic region",
            "Domestic deliveries to the northern, southern and coastal regions.",
            new BigDecimal("190.00"), new BigDecimal("25.00"), 4),

    INTERNATIONAL_NA(
            "North America",
            "Cross-border deliveries to the United States and Canada.",
            new BigDecimal("450.00"), new BigDecimal("60.00"), 7);

    private final String displayName;
    private final String description;
    private final BigDecimal baseCost;
    private final BigDecimal costPerKg;
    private final int estimatedBusinessDays;

    Zone(final String displayName, final String description,
         final BigDecimal baseCost, final BigDecimal costPerKg, final int estimatedBusinessDays) {
        this.displayName = displayName;
        this.description = description;
        this.baseCost = baseCost;
        this.costPerKg = costPerKg;
        this.estimatedBusinessDays = estimatedBusinessDays;
    }

    /**
     * Case-insensitive, whitespace-tolerant lookup.
     *
     * Returns an Optional instead of throwing: the caller decides what "not found" means
     * in its own context. The domain layer does not pick HTTP status codes.
     */
    public static Optional<Zone> fromCode(final String code) {
        if (code == null) {
            return Optional.empty();
        }
        final String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(zone -> zone.name().equals(normalized))
                .findFirst();
    }
}
