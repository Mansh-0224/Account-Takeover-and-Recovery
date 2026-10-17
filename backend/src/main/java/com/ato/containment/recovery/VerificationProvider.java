package com.ato.containment.recovery;

import com.ato.containment.model.User;

/**
 * How a recovery verification code reaches the account owner. RecoveryService
 * only ever calls {@link #deliver}; it never knows or cares whether that
 * means a real email, an SMS, or (today) the local/test stand-in below.
 *
 * Swapping in a real email/SMS provider later means adding one new class
 * that implements this interface -- RecoveryService does not change.
 */
public interface VerificationProvider {

    /**
     * Hands the raw code to the delivery channel. Implementations must
     * never write the raw code to a log -- see LocalVerificationProvider for
     * how the local/test stand-in gets the code to the caller instead.
     */
    DeliveryResult deliver(User user, String rawCode);

    /**
     * What came back from delivery. {@code testModeCode} is only ever
     * non-null for a non-production verification provider (see
     * LocalVerificationProvider) -- a real provider leaves it null, because
     * a real code must only ever reach the user through the real channel.
     */
    record DeliveryResult(boolean delivered, String testModeCode) {
    }
}
