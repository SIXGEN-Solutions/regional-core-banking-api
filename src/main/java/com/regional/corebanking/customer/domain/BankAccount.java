package com.regional.corebanking.customer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        Instant retrievedAt,
        String ncp, String age, String dev, String cha, String clc, String cli,
        String inti, String ife, String cfe, LocalDate dou,
        BigDecimal sin, String typ, BigDecimal sde, String suf
) {
    public BankAccount(String accountReference, String customerReference,
            String financialInstitutionCode, String maskedAccountIdentifier,
            String currency, AccountType accountType, AccountStatus status,
            Set<AccountRestriction> restrictions, Instant retrievedAt) {
        this(accountReference, customerReference, financialInstitutionCode,
                maskedAccountIdentifier, currency, accountType, status, restrictions,
                retrievedAt, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

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
