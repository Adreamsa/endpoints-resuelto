package com.teletubies.endpoints.shipping.exception;

import lombok.Getter;

import java.math.BigDecimal;

/** Raised when the package weight is above the hard service limit. */
@Getter
public class WeightLimitExceededException extends RuntimeException {

    private final BigDecimal receivedWeightKg;
    private final BigDecimal maxWeightKg;

    public WeightLimitExceededException(final BigDecimal receivedWeightKg, final BigDecimal maxWeightKg) {
        super("Package weight of " + receivedWeightKg.toPlainString()
                + " kg exceeds the maximum shippable weight of "
                + maxWeightKg.toPlainString() + " kg");
        this.receivedWeightKg = receivedWeightKg;
        this.maxWeightKg = maxWeightKg;
    }

}
