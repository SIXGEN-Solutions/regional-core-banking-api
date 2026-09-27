package com.regional.corebanking.confirmation.contract;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class PaymentConfirmationContractCompatibilityCharacterizationTest {
    @Test void sixApprovedOperationsRemainInRegionalAndSixpayReference() throws Exception {
        String regional=Files.readString(Path.of("contracts/openapi/regional-core-banking-api-v1.yaml"));
        String sixpay=Files.readString(Path.of("contracts/reference/sixpay/amplitude-payment-confirmation-api-v1.yaml"));
        for(String path:new String[]{"/api/v1/payment-confirmation-challenges:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/verifications:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/replacements:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}:",
                "/api/v1/payment-confirmation-challenge-lookups/{idempotencyKey}:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/revocations:"}){
            assertThat(regional).contains(path); assertThat(sixpay).contains(path);
        }
    }
}
