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
    void verifiesCustomerAccountAndKyc() {
        var service = new CustomerVerificationService(new StubPort());

        var result = service.verifyCustomer(new CustomerVerification.Request(
                "REGIONAL",
                new CustomerVerification.CustomerSubject(
                        null, null, "NIU-001", "Jane Doe", null, null
                ),
                new CustomerVerification.AccountSubject(
                        "ACC-001", null, null
                ),
                List.of("niu", "legalName", "phoneNumber", "email"),
                Instant.parse("2026-09-11T00:00:00Z")
        ));

        assertThat(result.outcome())
                .isEqualTo(CustomerVerification.Outcome.VERIFIED);
        assertThat(result.customerReference()).isEqualTo("CUS-001");
        assertThat(result.accountReference()).isEqualTo("ACC-001");
        assertThat(result.checks()).hasSize(11);
        assertThat(result.checks())
                .allMatch(check ->
                        check.result() == CustomerVerification.CheckResult.PASS);
    }

    private static final class StubPort implements CustomerBankingPort {

        @Override
        public List<CustomerSummary> searchCustomers(
                String financialInstitutionCode,
                String niu,
                String customerNumber
        ) {
            return List.of(new CustomerSummary(
                    "CUS-001", "C001", "REGIONAL", "NIU-001", "Jane Doe"
            ));
        }

        @Override
        public CustomerIdentity getCustomer(
                String financialInstitutionCode,
                String customerReference
        ) {
            Instant now = Instant.parse("2026-09-11T00:00:00Z");
            return new CustomerIdentity(
                    "CUS-001",
                    "C001",
                    "REGIONAL",
                    "NIU-001",
                    "Jane Doe",
                    "+237600000000",
                    "jane@example.com",
                    CustomerIdentity.KycStatus.COMPLETE,
                    List.of(
                            new CustomerIdentity.KycField("niu", null, true, true, now),
                            new CustomerIdentity.KycField("legalName", null, true, true, now),
                            new CustomerIdentity.KycField("phoneNumber", null, true, true, now),
                            new CustomerIdentity.KycField("email", null, true, true, now)
                    ),
                    now,
                    now
            );
        }

        @Override
        public List<BankAccount> getCustomerAccounts(
                String financialInstitutionCode,
                String customerReference,
                String rib,
                String iban
        ) {
            return List.of(new BankAccount(
                    "ACC-001",
                    "CUS-001",
                    "REGIONAL",
                    "****0001",
                    "XAF",
                    BankAccount.AccountType.CURRENT,
                    BankAccount.AccountStatus.ACTIVE,
                    Set.of(),
                    Instant.parse("2026-09-11T00:00:00Z")
            ));
        }
    }
}
