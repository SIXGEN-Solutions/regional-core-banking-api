package com.regional.corebanking.payment.application.exception;

public class PaymentExecutionNotFoundException extends RuntimeException {
    private final String paymentReference;

    public PaymentExecutionNotFoundException(String message) {
        this(message, null);
    }

    public PaymentExecutionNotFoundException(String message, String paymentReference) {
        super(message);
        this.paymentReference = paymentReference;
    }

    public String paymentReference() {
        return paymentReference;
    }
}
