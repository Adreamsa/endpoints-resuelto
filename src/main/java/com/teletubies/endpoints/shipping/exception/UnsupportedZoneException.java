package com.teletubies.endpoints.shipping.exception;

import lombok.Getter;

/**
 * Raised when a zone code is not part of the catalog.
 *
 * No Spring annotations and no HTTP status here: the exception describes what happened in
 * business terms, the web layer decides how that maps to a response.
 */
@Getter
public class UnsupportedZoneException extends RuntimeException {

    private final String field;
    private final String receivedCode;

    public UnsupportedZoneException(final String field, final String receivedCode) {
        super("Zone '" + receivedCode + "' is not supported");
        this.field = field;
        this.receivedCode = receivedCode;
    }

}
