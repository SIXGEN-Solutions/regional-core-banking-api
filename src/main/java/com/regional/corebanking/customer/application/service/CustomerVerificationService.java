package com.regional.corebanking.customer.application.service;

import com.regional.corebanking.customer.application.exception.CustomerNotFoundException;
import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class CustomerVerificationService implements CustomerVerificationUseCase {

    private final CustomerBankingPort bankingPort;

    public CustomerVerificationService(CustomerBankingPort bankingPort) {
        this.bankingPort = Objects.requireNonNull(bankingPort);
    }

    @Override
    public List<CustomerSummary> searchCustomers(
            String financialInstitutionCode,
            String niu,
            String customerNumber
    ) {
        if (blank(niu) && blank(customerNumber)) {
            throw new IllegalArgumentException("At least one search criterion is required");
        }

        return bankingPort.searchCustomers(
                required(financialInstitutionCode, "financialInstitutionCode"),
                trimToNull(niu),
                trimToNull(customerNumber)
        );
    }

    @Override
    public CustomerIdentity getCustomer(
            String financialInstitutionCode,
            String customerReference
    ) {
        CustomerIdentity identity = bankingPort.getCustomer(
                required(financialInstitutionCode, "financialInstitutionCode"),
                required(customerReference, "customerReference")
        );

        if (identity == null) {
            throw new CustomerNotFoundException(customerReference);
        }
        return identity;
    }

    @Override
    public List<BankAccount> getCustomerAccounts(
            String financialInstitutionCode,
            String customerReference,
            String rib,
            String iban
    ) {
        getCustomer(financialInstitutionCode, customerReference);

        return bankingPort.getCustomerAccounts(
                required(financialInstitutionCode, "financialInstitutionCode"),
                required(customerReference, "customerReference"),
                trimToNull(rib),
                trimToNull(iban)
        );
    }

    @Override
    public CustomerVerification.Result verifyCustomer(CustomerVerification.Request request) {
        Objects.requireNonNull(request, "request is required");

        String accountReference = required(request.accountReference(), "accountReference");
        String expectedNiu = required(request.expectedNiu(), "expectedNiu");
        String expectedHolder = required(request.expectedAccountHolder(), "expectedAccountHolder");
        List<String> requiredKyc = request.requiredKycFields() == null
                ? List.of()
                : List.copyOf(request.requiredKycFields());

        Instant now = Instant.now();
        List<CustomerVerification.Check> checks = new ArrayList<>();

        BankAccount account = bankingPort.findAccountByReference(accountReference);
        if (account == null) {
            checks.add(check(CustomerVerification.CheckType.ACCOUNT_EXISTS,
                    CustomerVerification.CheckResult.FAIL, "ERROR_ACCOUNT_EXIST", now));
            return result(CustomerVerification.Outcome.REJECTED,
                    null, accountReference, checks, null, null, now);
        }

        checks.add(check(CustomerVerification.CheckType.ACCOUNT_EXISTS,
                CustomerVerification.CheckResult.PASS, null, now));

        CustomerIdentity identity = bankingPort.getCustomer(
                account.financialInstitutionCode(),
                account.customerReference()
        );

        if (identity == null) {
            checks.add(check(CustomerVerification.CheckType.CUSTOMER_EXISTS,
                    CustomerVerification.CheckResult.FAIL, "ERROR", now));
            return result(CustomerVerification.Outcome.REJECTED,
                    account.customerReference(), accountReference, checks, null, account, now);
        }

        checks.add(check(CustomerVerification.CheckType.CUSTOMER_EXISTS,
                CustomerVerification.CheckResult.PASS, null, now));

        checks.add(check(
                CustomerVerification.CheckType.FINANCIAL_INSTITUTION_MATCHES,
                Objects.equals(identity.financialInstitutionCode(), account.financialInstitutionCode())
                        ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                Objects.equals(identity.financialInstitutionCode(), account.financialInstitutionCode())
                        ? null : "ERROR",
                now
        ));

        checks.add(check(
                CustomerVerification.CheckType.NIU_MATCHES,
                equalsNormalized(expectedNiu, identity.niu())
                        ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                equalsNormalized(expectedNiu, identity.niu()) ? null : "ERROR",
                now
        ));

        checks.add(check(
                CustomerVerification.CheckType.IDENTITY_MATCHES,
                equalsNormalized(expectedHolder, identity.legalName())
                        ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                equalsNormalized(expectedHolder, identity.legalName()) ? null : "ERROR",
                now
        ));

        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_BELONGS_TO_CUSTOMER,
                Objects.equals(account.customerReference(), identity.customerReference())
                        ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                Objects.equals(account.customerReference(), identity.customerReference())
                        ? null : "ERROR",
                now
        ));

        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_IS_ACTIVE,
                account.status() == BankAccount.AccountStatus.ACTIVE
                        ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                account.status() == BankAccount.AccountStatus.ACTIVE
                        ? null : "BANK_ACCOUNT_LOOK",
                now
        ));

        boolean restrictionUnknown =
                account.restrictions() == null
                        || account.restrictions().contains(BankAccount.AccountRestriction.UNKNOWN);

        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_NOT_BLOCKED,
                restrictionUnknown
                        ? CustomerVerification.CheckResult.UNKNOWN
                        : isBlocked(account)
                            ? CustomerVerification.CheckResult.FAIL
                            : CustomerVerification.CheckResult.PASS,
                restrictionUnknown ? null : isBlocked(account) ? "ACCOUNT_LOOK" : null,
                now
        ));

        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_NOT_OPPOSED,
                restrictionUnknown
                        ? CustomerVerification.CheckResult.UNKNOWN
                        : account.restrictions().contains(BankAccount.AccountRestriction.OPPOSITION)
                            ? CustomerVerification.CheckResult.FAIL
                            : CustomerVerification.CheckResult.PASS,
                restrictionUnknown ? null
                        : account.restrictions().contains(BankAccount.AccountRestriction.OPPOSITION)
                            ? "ACCOUNT_SUSPEND" : null,
                now
        ));

        if (!requiredKyc.isEmpty()) {
            checks.add(check(
                    CustomerVerification.CheckType.REQUIRED_KYC_PRESENT,
                    requiredKyc.stream().allMatch(code -> present(identity, code))
                            ? CustomerVerification.CheckResult.PASS
                            : CustomerVerification.CheckResult.FAIL,
                    requiredKyc.stream().allMatch(code -> present(identity, code))
                            ? null : "ERROR",
                    now
            ));

            CustomerVerification.CheckResult verifiedResult = kycVerifiedResult(identity, requiredKyc);
            checks.add(check(
                    CustomerVerification.CheckType.REQUIRED_KYC_VERIFIED,
                    verifiedResult,
                    verifiedResult == CustomerVerification.CheckResult.FAIL ? "ERROR" : null,
                    now
            ));
        }

        CustomerVerification.Outcome outcome = outcome(checks);

        return result(
                outcome,
                identity.customerReference(),
                account.accountReference(),
                checks,
                identity,
                account,
                now
        );
    }

    private static CustomerVerification.Outcome outcome(List<CustomerVerification.Check> checks) {
        if (checks.stream().anyMatch(c -> c.result() == CustomerVerification.CheckResult.FAIL)) {
            return CustomerVerification.Outcome.REJECTED;
        }
        if (checks.stream().anyMatch(c -> c.result() == CustomerVerification.CheckResult.UNKNOWN)) {
            return CustomerVerification.Outcome.INDETERMINATE;
        }
        return CustomerVerification.Outcome.VERIFIED;
    }

    private static CustomerVerification.CheckResult kycVerifiedResult(
            CustomerIdentity identity,
            List<String> requiredCodes
    ) {
        boolean unknown = false;

        for (String code : requiredCodes) {
            CustomerIdentity.KycField field = identity.kycFields().stream()
                    .filter(value -> code.equals(value.code()))
                    .findFirst()
                    .orElse(null);

            if (field == null || !field.present()) {
                return CustomerVerification.CheckResult.FAIL;
            }
            if (field.verified() == null) {
                unknown = true;
            } else if (!field.verified()) {
                return CustomerVerification.CheckResult.FAIL;
            }
        }

        return unknown
                ? CustomerVerification.CheckResult.UNKNOWN
                : CustomerVerification.CheckResult.PASS;
    }

    private static boolean present(CustomerIdentity identity, String code) {
        return identity.kycFields() != null
                && identity.kycFields().stream()
                .anyMatch(field -> code.equals(field.code()) && field.present());
    }

    private static boolean isBlocked(BankAccount account) {
        return account.status() == BankAccount.AccountStatus.BLOCKED
                || account.status() == BankAccount.AccountStatus.FROZEN
                || account.restrictions().contains(BankAccount.AccountRestriction.DEBIT_BLOCKED)
                || account.restrictions().contains(BankAccount.AccountRestriction.FULL_BLOCK);
    }

    private static boolean equalsNormalized(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value.strip()
                .replaceAll("\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private static CustomerVerification.Check check(
            CustomerVerification.CheckType type,
            CustomerVerification.CheckResult result,
            String reasonCode,
            Instant checkedAt
    ) {
        return new CustomerVerification.Check(type, result, reasonCode, checkedAt);
    }

    private static CustomerVerification.Result result(
            CustomerVerification.Outcome outcome,
            String customerReference,
            String accountReference,
            List<CustomerVerification.Check> checks,
            CustomerIdentity identity,
            BankAccount account,
            Instant now
    ) {
        return new CustomerVerification.Result(
                UUID.randomUUID(),
                now,
                outcome,
                customerReference,
                accountReference,
                List.copyOf(checks),
                identity,
                account
        );
    }

    private static String required(String value, String name) {
        if (blank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private static String trimToNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
