package com.ato.containment.exception;

/**
 * Throw this when a logged-in user is recognized but is not allowed to do the
 * thing they asked for (e.g. a normal user trying to create another account).
 * Turned into an HTTP 403 by GlobalExceptionHandler.
 *
 * This is different from a tenant-isolation failure: a resource that belongs
 * to another tenant should look like it does not exist (404, see
 * ResourceNotFoundException), not merely forbidden, so a caller cannot tell
 * the two situations apart and probe for valid ids in other tenants.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
