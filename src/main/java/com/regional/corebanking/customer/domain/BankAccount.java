package com.regional.corebanking.customer.domain;

import java.time.Instant;
import java.util.Set;

public record BankAccount(
        String accountReference,
        String customerReference,
        String financialInstitutionCode,
        String maskedAccountIdentifier,
        String currency,
        AccountType accountType,
        AccountStatus status,
        Set<AccountRestriction> restrictions,
        Instant retrievedAt
) {
    public enum AccountType {
        CURRENT, SAVINGS, OTHER, UNKNOWN
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, DORMANT, FROZEN, BLOCKED, CLOSED, UNKNOWN
    }

    public enum AccountRestriction {
        DEBIT_BLOCKED, CREDIT_BLOCKED, FULL_BLOCK, OPPOSITION,
        LEGAL_HOLD, GARNISHMENT, OTHER, UNKNOWN
    }
}
