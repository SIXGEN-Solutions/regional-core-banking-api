package com.regional.corebanking.customer.application.exception;

public final class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String customerReference) {
        super("Customer not found: " + customerReference);
    }
}
