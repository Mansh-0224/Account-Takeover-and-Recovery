package com.ato.containment.exception;

/**
 * Throw when a recovery request exists but is not usable the way the caller
 * is trying to use it right now (expired, already completed, wrong code,
 * too many attempts). Distinct from ResourceNotFoundException: the request
 * id is real, its current state just does not allow this action.
 */
public class RecoveryException extends RuntimeException {

    public RecoveryException(String message) {
        super(message);
    }
}
