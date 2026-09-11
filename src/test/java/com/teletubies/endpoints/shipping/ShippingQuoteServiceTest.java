package com.teletubies.endpoints.shipping;

import com.teletubies.endpoints.shipping.enums.Zone;
import com.teletubies.endpoints.shipping.exception.InvalidRouteException;
import com.teletubies.endpoints.shipping.exception.UnsupportedZoneException;
import com.teletubies.endpoints.shipping.exception.WeightLimitExceededException;
import com.teletubies.endpoints.shipping.dto.QuoteRequest;
import com.teletubies.endpoints.shipping.dto.QuoteResponse;
import com.teletubies.endpoints.shipping.service.ShippingQuoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Business rules only. Plain JUnit, no Spring context needed. */
class ShippingQuoteServiceTest {

    private final ShippingQuoteService service = new ShippingQuoteService();

    private QuoteRequest request(String origin, String destination, String weightKg) {
        return new QuoteRequest(origin, destination, new BigDecimal(weightKg));
    }

    @Test
    @DisplayName("Standard quote adds base cost and weight cost, with no surcharge")
    void standardQuote() {
        QuoteResponse response = service.quote(request("METROPOLITAN", "NATIONAL_CENTRAL", "3.50"));

        assertThat(response.costBreakdown().baseCost()).isEqualByComparingTo("140.00");
        assertThat(response.costBreakdown().weightCost()).isEqualByComparingTo("63.00");
        assertThat(response.costBreakdown().overweightSurcharge()).isEqualByComparingTo("0.00");
        assertThat(response.totalCost()).isEqualByComparingTo("203.00");
        assertThat(response.overweight()).isFalse();
        assertThat(response.delivery().businessDays()).isEqualTo(2);
        assertThat(response.currency()).isEqualTo("MXN");
    }

    @Test
    @DisplayName("50.00 kg is still the standard rate, it is the last weight without surcharge")
    void standardLimitIsInclusive() {
        QuoteResponse response = service.quote(request("METROPOLITAN", "NATIONAL_EXTENDED", "50.00"));

        assertThat(response.overweight()).isFalse();
        assertThat(response.costBreakdown().overweightSurcharge()).isEqualByComparingTo("0.00");
        assertThat(response.totalCost()).isEqualByComparingTo("1440.00");
        assertThat(response.delivery().businessDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("50.01 kg crosses into overweight: 40% surcharge and one extra day")
    void overweightStartsJustAboveTheStandardLimit() {
        QuoteResponse response = service.quote(request("METROPOLITAN", "NATIONAL_EXTENDED", "50.01"));

        assertThat(response.overweight()).isTrue();
        assertThat(response.costBreakdown().subtotal()).isEqualByComparingTo("1440.25");
        assertThat(response.costBreakdown().overweightSurcharge()).isEqualByComparingTo("576.10");
        assertThat(response.totalCost()).isEqualByComparingTo("2016.35");
        assertThat(response.delivery().businessDays()).isEqualTo(5);
        assertThat(response.delivery().description()).contains("overweight");
    }

    @Test
    @DisplayName("70.00 kg is accepted as overweight, it is the last shippable weight")
    void maxWeightIsInclusive() {
        QuoteResponse response = service.quote(request("METROPOLITAN", "NATIONAL_EXTENDED", "70.00"));

        assertThat(response.overweight()).isTrue();
        assertThat(response.costBreakdown().overweightSurcharge()).isEqualByComparingTo("776.00");
        assertThat(response.totalCost()).isEqualByComparingTo("2716.00");
    }

    @Test
    @DisplayName("70.01 kg is rejected as a business rule, not as a format error")
    void aboveMaxWeightIsRejected() {
        assertThatThrownBy(() -> service.quote(request("METROPOLITAN", "NATIONAL_EXTENDED", "70.01")))
                .isInstanceOf(WeightLimitExceededException.class)
                .hasMessageContaining("70.01")
                .hasMessageContaining("70.00");
    }

    @Test
    @DisplayName("Zone codes are matched ignoring case and surrounding whitespace")
    void zoneCodesAreNormalized() {
        QuoteResponse response = service.quote(request("  metropolitan ", "national_central", "1.00"));

        assertThat(response.origin()).isEqualTo("METROPOLITAN");
        assertThat(response.destination()).isEqualTo("NATIONAL_CENTRAL");
    }

    @Test
    @DisplayName("An unknown destination is a business failure and names the offending field")
    void unknownDestinationIsRejected() {
        assertThatThrownBy(() -> service.quote(request("METROPOLITAN", "MARS", "1.00")))
                .isInstanceOf(UnsupportedZoneException.class)
                .hasMessageContaining("MARS")
                .extracting(ex -> ((UnsupportedZoneException) ex).getField())
                .isEqualTo("destination");
    }

    @Test
    @DisplayName("An unknown origin is rejected too, and is reported before the destination")
    void unknownOriginIsRejected() {
        assertThatThrownBy(() -> service.quote(request("MARS", "ALSO_MARS", "1.00")))
                .isInstanceOf(UnsupportedZoneException.class)
                .extracting(ex -> ((UnsupportedZoneException) ex).getField())
                .isEqualTo("origin");
    }

    @Test
    @DisplayName("Shipping a package to its own zone is rejected")
    void sameOriginAndDestinationIsRejected() {
        assertThatThrownBy(() -> service.quote(request("METROPOLITAN", "METROPOLITAN", "1.00")))
                .isInstanceOf(InvalidRouteException.class)
                .hasMessageContaining("METROPOLITAN");
    }

    @Test
    @DisplayName("The catalog exposes every zone with its code, labels and rates")
    void catalogExposesEveryZone() {
        assertThat(service.getSupportedZones())
                .hasSize(Zone.values().length)
                .allSatisfy(zone -> {
                    assertThat(zone.code()).isNotBlank();
                    assertThat(zone.displayName()).isNotBlank();
                    assertThat(zone.description()).isNotBlank();
                    assertThat(zone.baseCost()).isPositive();
                    assertThat(zone.costPerKg()).isPositive();
                    assertThat(zone.estimatedBusinessDays()).isPositive();
                });
    }
}
