package com.regional.corebanking.customer.application.exception;

/**
 * Customer/account-relevant codes preserved from the functional legacy Core Banking API.
 * They are exposed through Regional Problem.code without changing the OpenAPI schema.
 */
public enum BankingErrorCode {
    ERROR,
    BANK_ERROR,
    BANK_PHONE_ERROR,
    BANK_ACCOUNT_LOCK,
    BANK_ACCOUNT_UNDEBIT,
    ACCOUNT_LOOK,
    ACCOUNT_SUSPEND,
    ERROR_ACCOUNT_EXIST
}
