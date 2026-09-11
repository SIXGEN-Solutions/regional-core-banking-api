package com.regional.corebanking.customer.application.service;

import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CustomerVerificationService implements CustomerVerificationUseCase {

    private static final List<String> MANDATORY_KYC =
            List.of("niu", "legalName", "phoneNumber", "email");

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
            throw new IllegalArgumentException(
                    "At least one search criterion is required"
            );
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
        return bankingPort.getCustomer(
                required(financialInstitutionCode, "financialInstitutionCode"),
                required(customerReference, "customerReference")
        );
    }

    @Override
    public List<BankAccount> getCustomerAccounts(
            String financialInstitutionCode,
            String customerReference,
            String rib,
            String iban
    ) {
        return bankingPort.getCustomerAccounts(
                required(financialInstitutionCode, "financialInstitutionCode"),
                required(customerReference, "customerReference"),
                trimToNull(rib),
                trimToNull(iban)
        );
    }

    @Override
    public CustomerVerification.Result verifyCustomer(
            CustomerVerification.Request request
    ) {
        Objects.requireNonNull(request, "request is required");
        required(request.financialInstitutionCode(), "financialInstitutionCode");
        Objects.requireNonNull(request.customer(), "customer is required");
        Objects.requireNonNull(request.account(), "account is required");

        if (!request.account().hasIdentifier()) {
            throw new IllegalArgumentException(
                    "At least one bank account identifier is required"
            );
        }

        if (request.requiredKycFields() == null
                || !request.requiredKycFields().containsAll(MANDATORY_KYC)) {
            throw new IllegalArgumentException(
                    "requiredKycFields must contain niu, legalName, phoneNumber and email"
            );
        }

        Instant now = Instant.now();
        List<CustomerVerification.Check> checks = new ArrayList<>();

        List<CustomerSummary> matches = searchCustomers(
                request.financialInstitutionCode(),
                request.customer().niu(),
                request.customer().customerNumber()
        );

        if (matches.isEmpty()) {
            checks.add(check(
                    CustomerVerification.CheckType.CUSTOMER_EXISTS,
                    CustomerVerification.CheckResult.FAIL,
                    "CUSTOMER_NOT_FOUND",
                    now
            ));
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    null,
                    null,
                    checks,
                    null,
                    null,
                    now
            );
        }

        CustomerSummary summary = matches.getFirst();
        checks.add(check(
                CustomerVerification.CheckType.CUSTOMER_EXISTS,
                CustomerVerification.CheckResult.PASS,
                null,
                now
        ));

        if (!Objects.equals(
                request.financialInstitutionCode(),
                summary.financialInstitutionCode()
        )) {
            checks.add(check(
                    CustomerVerification.CheckType.FINANCIAL_INSTITUTION_MATCHES,
                    CustomerVerification.CheckResult.FAIL,
                    "FINANCIAL_INSTITUTION_MISMATCH",
                    now
            ));
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    summary.customerReference(),
                    null,
                    checks,
                    null,
                    null,
                    now
            );
        }
        checks.add(check(
                CustomerVerification.CheckType.FINANCIAL_INSTITUTION_MATCHES,
                CustomerVerification.CheckResult.PASS,
                null,
                now
        ));

        if (!Objects.equals(request.customer().niu(), summary.niu())) {
            checks.add(check(
                    CustomerVerification.CheckType.NIU_MATCHES,
                    CustomerVerification.CheckResult.FAIL,
                    "NIU_MISMATCH",
                    now
            ));
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    summary.customerReference(),
                    null,
                    checks,
                    null,
                    null,
                    now
            );
        }
        checks.add(check(
                CustomerVerification.CheckType.NIU_MATCHES,
                CustomerVerification.CheckResult.PASS,
                null,
                now
        ));

        CustomerIdentity identity = getCustomer(
                request.financialInstitutionCode(),
                summary.customerReference()
        );

        if (!Objects.equals(request.customer().legalName(), identity.legalName())) {
            checks.add(check(
                    CustomerVerification.CheckType.IDENTITY_MATCHES,
                    CustomerVerification.CheckResult.FAIL,
                    "IDENTITY_MISMATCH",
                    now
            ));
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    summary.customerReference(),
                    null,
                    checks,
                    identity,
                    null,
                    now
            );
        }
        checks.add(check(
                CustomerVerification.CheckType.IDENTITY_MATCHES,
                CustomerVerification.CheckResult.PASS,
                null,
                now
        ));

        List<BankAccount> accounts = getCustomerAccounts(
                request.financialInstitutionCode(),
                summary.customerReference(),
                request.account().rib(),
                request.account().iban()
        );

        BankAccount account = accounts.stream()
                .filter(candidate ->
                        request.account().accountReference() == null
                                || request.account().accountReference()
                                        .equals(candidate.accountReference()))
                .findFirst()
                .orElse(null);

        if (account == null) {
            checks.add(check(
                    CustomerVerification.CheckType.ACCOUNT_EXISTS,
                    CustomerVerification.CheckResult.FAIL,
                    "ACCOUNT_NOT_FOUND",
                    now
            ));
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    summary.customerReference(),
                    null,
                    checks,
                    identity,
                    null,
                    now
            );
        }

        checks.add(check(CustomerVerification.CheckType.ACCOUNT_EXISTS,
                CustomerVerification.CheckResult.PASS, null, now));

        boolean belongs = Objects.equals(
                account.customerReference(), summary.customerReference()
        );
        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_BELONGS_TO_CUSTOMER,
                belongs ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                belongs ? null : "ACCOUNT_CUSTOMER_MISMATCH",
                now
        ));
        if (!belongs) {
            return result(
                    CustomerVerification.Outcome.REJECTED,
                    summary.customerReference(),
                    account.accountReference(),
                    checks,
                    identity,
                    account,
                    now
            );
        }

        boolean active = account.status() == BankAccount.AccountStatus.ACTIVE;
        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_IS_ACTIVE,
                active ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                active ? null : "ACCOUNT_NOT_ACTIVE",
                now
        ));

        boolean blocked = account.status() == BankAccount.AccountStatus.BLOCKED
                || account.status() == BankAccount.AccountStatus.FROZEN
                || account.restrictions().contains(BankAccount.AccountRestriction.DEBIT_BLOCKED)
                || account.restrictions().contains(BankAccount.AccountRestriction.FULL_BLOCK);
        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_NOT_BLOCKED,
                blocked ? CustomerVerification.CheckResult.FAIL
                        : CustomerVerification.CheckResult.PASS,
                blocked ? "ACCOUNT_BLOCKED" : null,
                now
        ));

        boolean opposed = account.restrictions()
                .contains(BankAccount.AccountRestriction.OPPOSITION);
        checks.add(check(
                CustomerVerification.CheckType.ACCOUNT_NOT_OPPOSED,
                opposed ? CustomerVerification.CheckResult.FAIL
                        : CustomerVerification.CheckResult.PASS,
                opposed ? "ACCOUNT_OPPOSED" : null,
                now
        ));

        boolean requiredPresent = request.requiredKycFields().stream()
                .allMatch(code -> identity.kycFields().stream()
                        .anyMatch(field -> code.equals(field.code()) && field.present()));
        checks.add(check(
                CustomerVerification.CheckType.REQUIRED_KYC_PRESENT,
                requiredPresent ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                requiredPresent ? null : "REQUIRED_KYC_MISSING",
                now
        ));

        boolean requiredVerified = request.requiredKycFields().stream()
                .allMatch(code -> identity.kycFields().stream()
                        .anyMatch(field -> code.equals(field.code()) && field.verified()));
        checks.add(check(
                CustomerVerification.CheckType.REQUIRED_KYC_VERIFIED,
                requiredVerified ? CustomerVerification.CheckResult.PASS
                        : CustomerVerification.CheckResult.FAIL,
                requiredVerified ? null : "REQUIRED_KYC_NOT_VERIFIED",
                now
        ));

        boolean allPass = checks.stream()
                .allMatch(value -> value.result() == CustomerVerification.CheckResult.PASS);

        return result(
                allPass
                        ? CustomerVerification.Outcome.VERIFIED
                        : CustomerVerification.Outcome.REJECTED,
                summary.customerReference(),
                account.accountReference(),
                checks,
                identity,
                account,
                now
        );
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
