package com.ato.containment.exception;

import java.time.Instant;

/**
 * Standard JSON body returned for every API error.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
