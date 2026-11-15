package com.regional.corebanking.customer.application.service;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerVerificationServiceTest {

    @Test
    void rejectsSearchWithoutCriterion() {
        var service = new CustomerVerificationService(new StubPort());

        assertThatThrownBy(() -> service.searchCustomers("REGIONAL", null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsIndeterminateWhenBankHasNoRestrictionOrKycVerificationEvidence() {
        var service = new CustomerVerificationService(new StubPort());

        var result = service.verifyCustomer(new CustomerVerification.Request(
                "001-123456-7",
                "NIU-001",
                "Jane Doe",
                List.of("niu", "legalName", "phoneNumber", "email"),
                Instant.parse("2026-09-13T00:00:00Z")
        ));

        assertThat(result.outcome())
                .isEqualTo(CustomerVerification.Outcome.INDETERMINATE);
        assertThat(result.customerReference()).isEqualTo("CUS-001");
        assertThat(result.accountReference()).isEqualTo("001-123456-7");
        assertThat(result.checks())
                .anyMatch(check -> check.result() == CustomerVerification.CheckResult.UNKNOWN);
    }

    @Test
    void acceptsMissingHolderWithoutInventingIdentityComparison() {
        var result = new CustomerVerificationService(new StubPort()).verifyCustomer(
                new CustomerVerification.Request("001-123456-7", "NIU-001", null,
                        List.of("niu"), Instant.parse("2026-09-13T00:00:00Z")));
        assertThat(result.outcome()).isEqualTo(CustomerVerification.Outcome.INDETERMINATE);
        assertThat(result.checks()).noneMatch(c -> c.type() == CustomerVerification.CheckType.IDENTITY_MATCHES);
        assertThat(result.checks()).anyMatch(c -> c.type() == CustomerVerification.CheckType.NIU_MATCHES
                && c.result() == CustomerVerification.CheckResult.PASS);
        assertThat(result.checks()).anyMatch(c -> c.type() == CustomerVerification.CheckType.REQUIRED_KYC_VERIFIED
                && c.result() == CustomerVerification.CheckResult.UNKNOWN);
    }

    @Test
    void rejectsIncorrectProvidedHolder() {
        var result = new CustomerVerificationService(new StubPort()).verifyCustomer(
                new CustomerVerification.Request("001-123456-7", "NIU-001", "Incorrect Name",
                        List.of("niu"), Instant.parse("2026-09-13T00:00:00Z")));
        assertThat(result.outcome()).isEqualTo(CustomerVerification.Outcome.REJECTED);
        assertThat(result.checks()).anyMatch(c -> c.type() == CustomerVerification.CheckType.IDENTITY_MATCHES
                && c.result() == CustomerVerification.CheckResult.FAIL);
    }

    @Test
    void comparesCorrectProvidedHolderAndTreatsBlankAsAbsent() {
        var service = new CustomerVerificationService(new StubPort());
        var correct = service.verifyCustomer(new CustomerVerification.Request(
                "001-123456-7", "NIU-001", "Jane Doe", List.of(), Instant.now()));
        assertThat(correct.checks()).anyMatch(c -> c.type() == CustomerVerification.CheckType.IDENTITY_MATCHES
                && c.result() == CustomerVerification.CheckResult.PASS);
        var blank = service.verifyCustomer(new CustomerVerification.Request(
                "001-123456-7", "NIU-001", "  ", List.of(), Instant.now()));
        assertThat(blank.checks()).noneMatch(c -> c.type() == CustomerVerification.CheckType.IDENTITY_MATCHES);
        assertThat(blank.outcome()).isEqualTo(CustomerVerification.Outcome.INDETERMINATE);
    }

    @Test
    void rejectsMissingMandatoryNiuEvenWhenHolderIsAbsent() {
        var service = new CustomerVerificationService(new StubPort());
        assertThatThrownBy(() -> service.verifyCustomer(new CustomerVerification.Request(
                "001-123456-7", null, null, List.of(), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class StubPort implements CustomerBankingPort {

        @Override
        public List<CustomerSummary> searchCustomers(
                String financialInstitutionCode,
                String niu,
                String customerNumber
        ) {
            return List.of(new CustomerSummary(
                    "CUS-001", "CUS-001", "REGIONAL", "NIU-001", "Jane Doe"
            ));
        }

        @Override
        public CustomerIdentity getCustomer(
                String financialInstitutionCode,
                String customerReference
        ) {
            Instant now = Instant.parse("2026-09-13T00:00:00Z");
            return new CustomerIdentity(
                    "CUS-001",
                    "CUS-001",
                    "REGIONAL",
                    "NIU-001",
                    "Jane Doe",
                    "+237600000000",
                    "jane@example.com",
                    CustomerIdentity.KycStatus.UNKNOWN,
                    List.of(
                            new CustomerIdentity.KycField("niu", "NIU-001", true, null, null),
                            new CustomerIdentity.KycField("legalName", "Jane Doe", true, null, null),
                            new CustomerIdentity.KycField("phoneNumber", "+237600000000", true, null, null),
                            new CustomerIdentity.KycField("email", "jane@example.com", true, null, null)
                    ),
                    null,
                    now
            );
        }

        @Override
        public BankAccount findAccountByAgencyAndNumber(
                String financialInstitutionCode,
                String branchId,
                String accountNumber
        ) {
            if (!"REGIONAL".equals(financialInstitutionCode)
                    || !"001".equals(branchId)
                    || !"123456".equals(accountNumber)) {
                return null;
            }

            return findAccountByReference("001-123456-7");
        }

        @Override
        public List<BankAccount> getCustomerAccounts(
                String financialInstitutionCode,
                String customerReference,
                String rib,
                String iban
        ) {
            return List.of(findAccountByReference("001-123456-7"));
        }

        @Override
        public BankAccount findAccountByReference(String accountReference) {
            return new BankAccount(
                    "001-123456-7",
                    "CUS-001",
                    "REGIONAL",
                    "**********56-7",
                    "001",
                    BankAccount.AccountType.UNKNOWN,
                    BankAccount.AccountStatus.ACTIVE,
                    Set.of(BankAccount.AccountRestriction.UNKNOWN),
                    Instant.parse("2026-09-13T00:00:00Z")
            );
        }
    }
}
