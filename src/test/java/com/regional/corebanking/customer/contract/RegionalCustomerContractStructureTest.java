package com.regional.corebanking.customer.contract;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Checks the Regional-owned contract only; no SIXPAY contract snapshots. */
class RegionalCustomerContractStructureTest {
    private static final Path REGIONAL = Path.of("contracts/openapi/regional-core-banking-api-v1.yaml");

    @Test
    void verificationAccountUsesSameBankAccountAsAccountListing() throws Exception {
        String regional = Files.readString(REGIONAL);
        assertThat(regional).doesNotContain("    AccountSummary:");
        assertThat(regional).doesNotContain("#/components/schemas/AccountSummary");
        assertThat(regional).contains("#/components/schemas/BankAccount");
        assertThat(regional).contains("sin: { type: number");
    }

    @Test
    void regionalVerificationRequestMakesOnlyHolderOptional() throws Exception {
        String regional = Files.readString(REGIONAL);
        assertThat(regional)
                .contains("required: [accountReference, expectedNiu, requestedAt]")
                .doesNotContain("required: [accountReference, expectedNiu, expectedAccountHolder, requestedAt]")
                .contains("accountReference")
                .contains("expectedNiu")
                .contains("expectedAccountHolder")
                .contains("requiredKycFields")
                .contains("requestedAt");
    }
}
