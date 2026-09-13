package com.regional.corebanking.customer.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerContractCompatibilityCharacterizationTest {

    private static final Path REGIONAL =
            Path.of("contracts/openapi/regional-core-banking-api-v1.yaml");
    private static final Path SIXPAY_REFERENCE =
            Path.of("contracts/reference/sixpay/amplitude-customer-verification-api-v1.yaml");

    @Test
    void retainedCustomerPathsExistInBothContracts() throws Exception {
        String regional = Files.readString(REGIONAL);
        String sixpay = Files.readString(SIXPAY_REFERENCE);

        for (String path : new String[]{
                "/api/v1/customers:",
                "/api/v1/customers/{customerReference}:",
                "/api/v1/customer-verifications:"
        }) {
            assertThat(regional).contains(path);
            assertThat(sixpay).contains(path);
        }
    }

    @Test
    void customerSearchCoreWireFieldsRemainCommon() throws Exception {
        String regional = Files.readString(REGIONAL);
        String sixpay = Files.readString(SIXPAY_REFERENCE);

        for (String token : new String[]{
                "customerReference",
                "customerNumber",
                "financialInstitutionCode",
                "niu",
                "legalName"
        }) {
            assertThat(regional).contains(token);
            assertThat(sixpay).contains(token);
        }
    }

    @Test
    void verificationRequestShapeConflictRemainsExplicit() throws Exception {
        String regional = Files.readString(REGIONAL);
        String sixpay = Files.readString(SIXPAY_REFERENCE);

        assertThat(regional)
                .contains("required: [accountReference, expectedNiu, expectedAccountHolder, requestedAt]")
                .contains("expectedNiu")
                .contains("expectedAccountHolder");

        assertThat(sixpay)
                .contains("CustomerVerificationSubject")
                .contains("AccountVerificationSubject");
    }
}
