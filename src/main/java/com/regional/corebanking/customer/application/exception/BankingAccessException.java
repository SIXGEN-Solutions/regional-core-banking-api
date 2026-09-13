package com.regional.corebanking.customer.application.exception;

public final class BankingAccessException extends RuntimeException {

    private final BankingErrorCode code;

    public BankingAccessException(BankingErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BankingAccessException(BankingErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BankingErrorCode code() {
        return code;
    }
}
