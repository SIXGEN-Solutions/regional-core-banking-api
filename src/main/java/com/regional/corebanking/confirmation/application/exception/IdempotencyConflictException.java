package com.regional.corebanking.confirmation.application.exception;
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Idempotency-Key already used for a different logical request"); }
}
