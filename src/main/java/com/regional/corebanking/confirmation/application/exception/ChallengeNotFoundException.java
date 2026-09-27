package com.regional.corebanking.confirmation.application.exception;
public class ChallengeNotFoundException extends RuntimeException {
    public ChallengeNotFoundException(String reference) { super("Challenge not found: " + reference); }
}
