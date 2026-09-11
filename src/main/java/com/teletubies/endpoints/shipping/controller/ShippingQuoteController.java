package com.teletubies.endpoints.shipping.controller;

import com.teletubies.endpoints.shipping.dto.QuoteRequest;
import com.teletubies.endpoints.shipping.dto.QuoteResponse;
import com.teletubies.endpoints.shipping.dto.SupportedZoneResponse;
import com.teletubies.endpoints.shipping.service.ShippingQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The two endpoints of the API. Receives, delegates, responds. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ShippingQuoteController {

    private final ShippingQuoteService shippingQuoteService;

    /**
     * 200 with the catalog. An empty catalog would still be 200: "there are no zones" is a
     * valid answer to a well-formed question, not a missing resource.
     */
    @GetMapping("/zones")
    public ResponseEntity<List<SupportedZoneResponse>> listZones() {
        List<SupportedZoneResponse> zones = shippingQuoteService.getSupportedZones();
        log.debug("Zone catalog requested, {} entries", zones.size());
        return ResponseEntity.ok(zones);
    }

    /**
     * 200 and not 201: quoting calculates over the payload, it does not create a resource,
     * so there would be no Location to return.
     */
    @PostMapping("/quotes")
    public ResponseEntity<QuoteResponse> createQuote(@Valid @RequestBody final QuoteRequest request) {
        log.debug("Quoting {} -> {} for {} kg",
                request.origin(), request.destination(), request.weightKg());
        return ResponseEntity.ok(shippingQuoteService.quote(request));
    }
}
