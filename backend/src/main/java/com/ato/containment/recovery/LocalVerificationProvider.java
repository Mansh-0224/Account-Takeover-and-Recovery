package com.ato.containment.recovery;

import com.ato.containment.model.User;
import org.springframework.stereotype.Component;

/**
 * No real email/SMS infrastructure exists in this project, so this is a
 * deliberate, clearly-labeled local/test stand-in rather than a fake email
 * sender pretending to work. It does NOT send anything anywhere.
 *
 * Instead it hands the raw code back through {@link VerificationProvider.DeliveryResult#testModeCode()},
 * which RecoveryController puts directly in the API response -- never in a
 * log file, never written to disk. This is a deliberate test-mode behavior,
 * not a logging exception: the code is still "never logged" exactly as
 * required.
 *
 * Replace this class with a real email/SMS-backed implementation of
 * VerificationProvider before this ever runs somewhere real; nothing else
 * in the recovery flow needs to change.
 */
@Component
public class LocalVerificationProvider implements VerificationProvider {

    @Override
    public DeliveryResult deliver(User user, String rawCode) {
        return new DeliveryResult(true, rawCode);
    }
}
