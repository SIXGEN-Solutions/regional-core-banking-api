package com.regional.corebanking.payment.application.exception;

public class PaymentExecutionIdempotencyConflictException extends RuntimeException {
    public PaymentExecutionIdempotencyConflictException(String message) {
        super(message);
    }
}
