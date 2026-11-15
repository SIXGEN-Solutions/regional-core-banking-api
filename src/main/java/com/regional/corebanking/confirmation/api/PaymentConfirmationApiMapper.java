package com.regional.corebanking.confirmation.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.domain.ConfirmationBusinessCode;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.ConfirmationCommand;
import com.regional.corebanking.generated.model.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PaymentConfirmationApiMapper {
    private final ObjectMapper objectMapper;

    public PaymentConfirmationApiMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConfirmationCommand.Create toDomain(CreatePaymentConfirmationChallengeRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> amount = map(payload.get("amount"));
        return new ConfirmationCommand.Create(
                string(payload.get("paymentReference")),
                string(payload.get("customerReference")),
                string(payload.get("debtorAccountReference")),
                decimal(amount.get("amount")),
                string(amount.get("currency")));
    }

    public ConfirmationCommand.Verify toDomain(VerifyPaymentConfirmationChallengeRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
        return new ConfirmationCommand.Verify(
                string(payload.get("paymentReference")),
                string(payload.get("otp")).toCharArray());
    }

    public ConfirmationCommand.Replace toDomain(ReplacePaymentConfirmationChallengeRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
        return new ConfirmationCommand.Replace(string(payload.get("paymentReference")));
    }

    public ConfirmationCommand.Revoke toDomain(RevokePaymentConfirmationChallengeRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
        return new ConfirmationCommand.Revoke(
                string(payload.get("paymentReference")),
                string(payload.get("reasonCode")));
    }

    public PaymentConfirmationChallengeResult toChallengeResult(ConfirmationChallenge challenge) {
        return objectMapper.convertValue(payload(challenge), PaymentConfirmationChallengeResult.class);
    }

    public PaymentConfirmationVerificationResult toVerificationResult(ConfirmationChallenge challenge) {
        return objectMapper.convertValue(payload(challenge), PaymentConfirmationVerificationResult.class);
    }

    private Map<String, Object> payload(ConfirmationChallenge challenge) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentReference", challenge.paymentReference());
        payload.put("challengeReference", challenge.challengeReference());
        payload.put("challengeStatus", challenge.status().name());
        payload.put("businessCode", challenge.businessCode().name());
        payload.put("deliveryChannel",
                challenge.deliveryChannels().size() == 1
                        ? challenge.deliveryChannels().iterator().next().name()
                        : null);
        payload.put("sentAt", challenge.sentAt());
        payload.put("expiresAt", challenge.expiresAt());
        payload.put("verifiedAt", challenge.verifiedAt());
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
