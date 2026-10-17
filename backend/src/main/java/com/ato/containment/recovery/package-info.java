/**
 * Account recovery: RecoveryService holds the actual workflow (request ->
 * verify -> complete) and never touches a verification code directly --
 * that goes through the VerificationProvider abstraction, so swapping
 * LocalVerificationProvider for a real email/SMS provider later is a
 * one-class change, not a rewrite of the recovery logic.
 */
package com.ato.containment.recovery;
