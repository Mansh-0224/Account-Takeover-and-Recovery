package com.ato.containment.exception;

/**
 * Throw this when a request needs a logged-in user but none is present (no
 * session, or the session expired). Turned into an HTTP 401 by GlobalExceptionHandler.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
