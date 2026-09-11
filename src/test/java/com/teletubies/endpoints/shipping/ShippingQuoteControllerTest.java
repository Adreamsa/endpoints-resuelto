package com.teletubies.endpoints.shipping;

import com.teletubies.endpoints.shipping.exception.ApiExceptionHandler;
import com.teletubies.endpoints.shipping.controller.ShippingQuoteController;
import com.teletubies.endpoints.shipping.service.ShippingQuoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST contract: status codes and response shape, not arithmetic. */
@WebMvcTest(ShippingQuoteController.class)
@Import({ShippingQuoteService.class, ApiExceptionHandler.class})
class ShippingQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /zones returns 200 with the catalog")
    void zonesReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[0].baseCost").exists())
                .andExpect(jsonPath("$[0].estimatedBusinessDays").exists());
    }

    @Test
    @DisplayName("A valid quote returns 200 with the cost breakdown")
    void validQuoteReturns200() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "NATIONAL_CENTRAL",
                  "weightKg": 3.50
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overweight").value(false))
                .andExpect(jsonPath("$.costBreakdown.weightCost").value(63.00))
                .andExpect(jsonPath("$.totalCost").value(203.00))
                .andExpect(jsonPath("$.delivery.businessDays").value(2))
                .andExpect(jsonPath("$.currency").value("MXN"));
    }

    @Test
    @DisplayName("A 55 kg package returns 200 flagged as overweight")
    void overweightQuoteReturns200() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "NATIONAL_EXTENDED",
                  "weightKg": 55.00
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overweight").value(true))
                .andExpect(jsonPath("$.costBreakdown.overweightSurcharge").value(626.00))
                .andExpect(jsonPath("$.totalCost").value(2191.00))
                .andExpect(jsonPath("$.delivery.businessDays").value(5));
    }

    @Test
    @DisplayName("A missing field returns 400 and names the field that failed")
    void missingFieldReturns400() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "NATIONAL_CENTRAL"
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("weightKg"));
    }

    @Test
    @DisplayName("Every invalid field is reported in the same response, not just the first")
    void allInvalidFieldsAreReportedAtOnce() throws Exception {
        String body = """
                {
                  "origin": "",
                  "destination": "   ",
                  "weightKg": 0
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(3)))
                .andExpect(jsonPath("$.details[0].field").value("destination"))
                .andExpect(jsonPath("$.details[1].field").value("origin"))
                .andExpect(jsonPath("$.details[2].field").value("weightKg"));
    }

    @Test
    @DisplayName("A negative weight returns 400: it is a range problem, not a business rule")
    void negativeWeightReturns400() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "NATIONAL_CENTRAL",
                  "weightKg": -2.00
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Broken JSON returns 400 without leaking the parser message")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("An unsupported destination returns 422: well formed, semantically invalid")
    void unsupportedZoneReturns422() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "MARS",
                  "weightKg": 3.50
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ZONE"))
                .andExpect(jsonPath("$.details[0].field").value("destination"));
    }

    @Test
    @DisplayName("A package above 70 kg returns 422, not 400")
    void weightAboveMaxReturns422() throws Exception {
        String body = """
                {
                  "origin": "METROPOLITAN",
                  "destination": "NATIONAL_EXTENDED",
                  "weightKg": 71.00
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WEIGHT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Origin equal to destination returns 422")
    void sameOriginAndDestinationReturns422() throws Exception {
        String body = """
                {
                  "origin": "NATIONAL_CENTRAL",
                  "destination": "national_central",
                  "weightKg": 3.50
                }
                """;

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORIGIN_EQUALS_DESTINATION"));
    }

    @Test
    @DisplayName("An unknown URL returns 404 using the same error contract")
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("A wrong method on an existing URL returns 405")
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/v1/quotes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
