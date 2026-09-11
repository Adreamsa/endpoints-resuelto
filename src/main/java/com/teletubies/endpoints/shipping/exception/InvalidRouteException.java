package com.teletubies.endpoints.shipping.exception;

import lombok.Getter;

/** Raised when origin and destination resolve to the same zone. */
@Getter
public class InvalidRouteException extends RuntimeException {

    private final String zoneCode;

    public InvalidRouteException(final String zoneCode) {
        super("Origin and destination cannot both be '" + zoneCode + "'");
        this.zoneCode = zoneCode;
    }

}
