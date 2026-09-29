package com.regional.corebanking.payment.application.exception;

public class PaymentExecutionUnavailableException extends RuntimeException {
    public PaymentExecutionUnavailableException(String message) {
        super(message);
    }
}
