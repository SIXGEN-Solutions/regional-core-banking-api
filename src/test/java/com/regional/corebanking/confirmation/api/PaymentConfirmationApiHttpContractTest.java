package com.regional.corebanking.confirmation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentConfirmationApiHttpContractTest {
    private static final String CID = "11111111-1111-1111-1111-111111111111";
    private static final String FI = "REGIONAL";
    private static final String CH = "CHL-001";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mvc = MockMvcBuilders.standaloneSetup(
                        new PaymentConfirmationApiAdapter(new StubUseCase(), new PaymentConfirmationApiMapper(mapper)))
                .setControllerAdvice(new PaymentConfirmationApiExceptionHandler())
                .build();
    }

    @Test
    void createsChallenge() throws Exception {
        mvc.perform(post("/api/v1/payment-confirmation-challenges")
                        .header("X-Correlation-ID", CID)
                        .header("X-Financial-Institution-Code", FI)
                        .header("Idempotency-Key", "create-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-0123456789ABCDEFGHJKMNPQRS\",\"customerReference\":\"CUS-001\",\"debtorAccountReference\":\"001-123456-7\",\"amount\":{\"amount\":1000.00,\"currency\":\"XAF\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeReference").value(CH))
                .andExpect(jsonPath("$.challengeStatus").value("ACTIVE"));
    }

    @Test
    void verifiesChallenge() throws Exception {
        mvc.perform(post("/api/v1/payment-confirmation-challenges/" + CH + "/verifications")
                        .header("X-Correlation-ID", CID).header("X-Financial-Institution-Code", FI)
                        .header("Idempotency-Key", "verify-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-0123456789ABCDEFGHJKMNPQRS\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeStatus").value("VERIFIED"));
    }

    @Test
    void replacesChallenge() throws Exception {
        mvc.perform(post("/api/v1/payment-confirmation-challenges/" + CH + "/replacements")
                        .header("X-Correlation-ID", CID).header("X-Financial-Institution-Code", FI)
                        .header("Idempotency-Key", "replace-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-0123456789ABCDEFGHJKMNPQRS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeReference").value("CHL-002"));
    }

    @Test
    void getsChallenge() throws Exception {
        mvc.perform(get("/api/v1/payment-confirmation-challenges/" + CH)
                        .header("X-Correlation-ID", CID).header("X-Financial-Institution-Code", FI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeReference").value(CH));
    }

    @Test
    void recoversByIdempotencyKey() throws Exception {
        mvc.perform(get("/api/v1/payment-confirmation-challenge-lookups/create-key-0001")
                        .header("X-Correlation-ID", CID).header("X-Financial-Institution-Code", FI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeReference").value(CH));
    }

    @Test
    void revokesChallenge() throws Exception {
        mvc.perform(post("/api/v1/payment-confirmation-challenges/" + CH + "/revocations")
                        .header("X-Correlation-ID", CID).header("X-Financial-Institution-Code", FI)
                        .header("Idempotency-Key", "revoke-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-0123456789ABCDEFGHJKMNPQRS\",\"reasonCode\":\"PAYMENT_CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeStatus").value("REVOKED"));
    }

    private static final class StubUseCase implements PaymentConfirmationUseCase {
        public ConfirmationChallenge create(String i,String k,ConfirmationCommand.Create c){ return challenge(CH,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE); }
        public ConfirmationChallenge verify(String i,String k,String r,ConfirmationCommand.Verify c){ return challenge(r,ChallengeStatus.VERIFIED,ConfirmationBusinessCode.OTP_VERIFIED); }
        public ConfirmationChallenge replace(String i,String k,String r,ConfirmationCommand.Replace c){ return challenge("CHL-002",ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE); }
        public ConfirmationChallenge get(String i,String r){ return challenge(r,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE); }
        public ConfirmationChallenge recover(String i,String k){ return challenge(CH,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE); }
        public ConfirmationChallenge revoke(String i,String k,String r,ConfirmationCommand.Revoke c){ return challenge(r,ChallengeStatus.REVOKED,ConfirmationBusinessCode.CHALLENGE_REVOKED); }
    }

    private static ConfirmationChallenge challenge(String ref, ChallengeStatus status, ConfirmationBusinessCode code) {
        Instant now = Instant.parse("2026-09-27T18:00:00Z");
        return new ConfirmationChallenge(
                ref, "PAY-0123456789ABCDEFGHJKMNPQRS", "CUS-001", "001-123456-7",
                new BigDecimal("1000.00"), "XAF", "verifier", "test-v1",
                status, code, 0, 0, Set.of(DeliveryChannel.SMS),
                now, now.plusSeconds(300), status == ChallengeStatus.VERIFIED ? now : null,
                null, status == ChallengeStatus.REVOKED ? now : null);
    }
}
