package com.ato.containment.exception;

/**
 * Throw this when something requested by the client does not exist.
 * It is turned into an HTTP 404 by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
