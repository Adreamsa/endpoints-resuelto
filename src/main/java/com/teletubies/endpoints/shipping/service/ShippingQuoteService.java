package com.teletubies.endpoints.shipping.service;

import com.teletubies.endpoints.shipping.enums.Zone;
import com.teletubies.endpoints.shipping.exception.InvalidRouteException;
import com.teletubies.endpoints.shipping.exception.UnsupportedZoneException;
import com.teletubies.endpoints.shipping.exception.WeightLimitExceededException;
import com.teletubies.endpoints.shipping.dto.CostBreakdownResponse;
import com.teletubies.endpoints.shipping.dto.DeliveryEstimateResponse;
import com.teletubies.endpoints.shipping.dto.QuoteRequest;
import com.teletubies.endpoints.shipping.dto.QuoteResponse;
import com.teletubies.endpoints.shipping.dto.SupportedZoneResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Business rules of the quoting service. Stateless, and unaware of HTTP.
 *
 * Weight policy (see README for the reasoning behind it):
 *   0 < w <= 50 kg   standard rate
 *   50 < w <= 70 kg  standard rate plus an overweight surcharge and one extra day
 *   w > 70 kg        rejected
 */
@Slf4j
@Service
public class ShippingQuoteService {

    private static final String CURRENCY = "MXN";
    private static final int MONEY_SCALE = 2;
    private static final int WEIGHT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal STANDARD_WEIGHT_LIMIT_KG = new BigDecimal("50.00");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("70.00");
    private static final BigDecimal OVERWEIGHT_SURCHARGE_RATE = new BigDecimal("0.40");
    private static final int OVERWEIGHT_EXTRA_BUSINESS_DAYS = 1;

    public List<SupportedZoneResponse> getSupportedZones() {
        return Arrays.stream(Zone.values())
                .map(zone -> new SupportedZoneResponse(
                        zone.name(),
                        zone.getDisplayName(),
                        zone.getDescription(),
                        zone.getBaseCost(),
                        zone.getCostPerKg(),
                        zone.getEstimatedBusinessDays()))
                .toList();
    }

    public QuoteResponse quote(final QuoteRequest request) {
        final Zone origin = resolveZone("origin", request.origin());
        final Zone destination = resolveZone("destination", request.destination());

        if (origin == destination) {
            log.warn("Quote rejected, origin equals destination: '{}'", origin.name());
            throw new InvalidRouteException(origin.name());
        }

        BigDecimal weightKg = request.weightKg();
        if (weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            log.warn("Quote rejected, weight {} kg above the {} kg limit", weightKg, MAX_WEIGHT_KG);
            throw new WeightLimitExceededException(weightKg, MAX_WEIGHT_KG);
        }
        weightKg = weightKg.setScale(WEIGHT_SCALE, ROUNDING);

        final boolean overweight = weightKg.compareTo(STANDARD_WEIGHT_LIMIT_KG) > 0;

        final BigDecimal baseCost = destination.getBaseCost().setScale(MONEY_SCALE, ROUNDING);
        final BigDecimal weightCost = destination.getCostPerKg()
                .multiply(weightKg)
                .setScale(MONEY_SCALE, ROUNDING);
        final BigDecimal subtotal = baseCost.add(weightCost);

        final BigDecimal overweightSurcharge = overweight
                ? subtotal.multiply(OVERWEIGHT_SURCHARGE_RATE).setScale(MONEY_SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        final BigDecimal totalCost = subtotal.add(overweightSurcharge);

        final int businessDays = destination.getEstimatedBusinessDays()
                + (overweight ? OVERWEIGHT_EXTRA_BUSINESS_DAYS : 0);

        return new QuoteResponse(
                origin.name(),
                destination.name(),
                weightKg,
                CURRENCY,
                overweight,
                new CostBreakdownResponse(baseCost, weightCost, subtotal, overweightSurcharge),
                totalCost,
                new DeliveryEstimateResponse(businessDays, describeDelivery(businessDays, overweight)));
    }

    /**
     * Bean Validation only guaranteed the code is not blank. Deciding whether it exists
     * requires the catalog, and that is this layer's job.
     */
    private Zone resolveZone(final String field, final String code) {
        return Zone.fromCode(code)
                .orElseThrow(() -> {
                    log.warn("Quote rejected, unsupported {} zone: '{}'", field, code);
                    return new UnsupportedZoneException(field, code);
                });
    }

    private String describeDelivery(final int businessDays, final boolean overweight) {
        final String base = "Estimated delivery in " + businessDays
                + (businessDays == 1 ? " business day." : " business days.");
        return overweight
                ? base + " Includes " + OVERWEIGHT_EXTRA_BUSINESS_DAYS
                        + " extra business day for special overweight handling."
                : base;
    }
}
