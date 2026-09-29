package com.regional.corebanking.payment.application.exception;

public class PaymentExecutionNotFoundException extends RuntimeException {
    public PaymentExecutionNotFoundException(String message) {
        super(message);
    }
}
