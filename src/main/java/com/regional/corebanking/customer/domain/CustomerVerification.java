package com.regional.corebanking.customer.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerVerification {

    private CustomerVerification() {
    }

    public record Request(
            String financialInstitutionCode,
            CustomerSubject customer,
            AccountSubject account,
            List<String> requiredKycFields,
            Instant requestedAt
    ) {
    }

    public record CustomerSubject(
            String customerReference,
            String customerNumber,
            String niu,
            String legalName,
            String phoneNumber,
            String email
    ) {
    }

    public record AccountSubject(
            String accountReference,
            String rib,
            String iban
    ) {
        public boolean hasIdentifier() {
            return notBlank(accountReference) || notBlank(rib) || notBlank(iban);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record Result(
            UUID verificationId,
            Instant verifiedAt,
            Outcome outcome,
            String customerReference,
            String accountReference,
            List<Check> checks,
            CustomerIdentity identity,
            BankAccount account
    ) {
    }

    public record Check(
            CheckType type,
            CheckResult result,
            String reasonCode,
            Instant checkedAt
    ) {
    }

    public enum Outcome {
        VERIFIED, REJECTED, INDETERMINATE
    }

    public enum CheckResult {
        PASS, FAIL, UNKNOWN
    }

    public enum CheckType {
        CUSTOMER_EXISTS,
        FINANCIAL_INSTITUTION_MATCHES,
        NIU_MATCHES,
        IDENTITY_MATCHES,
        ACCOUNT_EXISTS,
        ACCOUNT_BELONGS_TO_CUSTOMER,
        ACCOUNT_IS_ACTIVE,
        ACCOUNT_NOT_BLOCKED,
        ACCOUNT_NOT_OPPOSED,
        REQUIRED_KYC_PRESENT,
        REQUIRED_KYC_VERIFIED
    }
}
