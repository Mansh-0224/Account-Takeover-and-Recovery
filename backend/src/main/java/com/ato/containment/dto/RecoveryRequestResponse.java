package com.ato.containment.dto;

import java.time.Instant;

/**
 * {@code testModeCode} is only present because this project's VerificationProvider
 * is the local/test stand-in (see recovery/LocalVerificationProvider) -- a
 * real email/SMS provider would leave it null and the code would only ever
 * reach the user through that real channel.
 */
public record RecoveryRequestResponse(String requestId, Instant expiresAt, String testModeCode, String message) {

    /** Same shape whether or not the email matched an account -- never reveals which. */
    public static RecoveryRequestResponse generic() {
        return new RecoveryRequestResponse(null, null, null,
                "If that email has an account, recovery instructions have been sent.");
    }
}
