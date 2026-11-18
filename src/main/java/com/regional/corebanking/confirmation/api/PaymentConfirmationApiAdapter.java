package com.regional.corebanking.confirmation.api;

import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.generated.api.PaymentConfirmationApi;
import com.regional.corebanking.generated.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentConfirmationApiAdapter implements PaymentConfirmationApi {
    private final PaymentConfirmationUseCase useCase;
    private final PaymentConfirmationApiMapper mapper;

    public PaymentConfirmationApiAdapter(PaymentConfirmationUseCase useCase, PaymentConfirmationApiMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PaymentConfirmationChallengeResult> createPaymentConfirmationChallenge(
            UUID xCorrelationID, String xFinancialInstitutionCode, String idempotencyKey,
            CreatePaymentConfirmationChallengeRequest request) {
        return ResponseEntity.ok(mapper.toChallengeResult(
                useCase.create(xFinancialInstitutionCode, idempotencyKey, mapper.toDomain(request))));
    }

    @Override
    public ResponseEntity<PaymentConfirmationVerificationResult> verifyPaymentConfirmationChallenge(
            UUID xCorrelationID, String xFinancialInstitutionCode, String idempotencyKey,
            String challengeReference, VerifyPaymentConfirmationChallengeRequest request) {
        return ResponseEntity.ok(mapper.toVerificationResult(
                useCase.verify(xFinancialInstitutionCode, idempotencyKey, challengeReference, mapper.toDomain(request))));
    }

    @Override
    public ResponseEntity<PaymentConfirmationChallengeResult> replacePaymentConfirmationChallenge(
            UUID xCorrelationID, String xFinancialInstitutionCode, String idempotencyKey,
            String challengeReference, ReplacePaymentConfirmationChallengeRequest request) {
        return ResponseEntity.ok(mapper.toChallengeResult(
                useCase.replace(xFinancialInstitutionCode, idempotencyKey, challengeReference, mapper.toDomain(request))));
    }

    @Override
    public ResponseEntity<PaymentConfirmationChallengeResult> getPaymentConfirmationChallenge(
            UUID xCorrelationID, String xFinancialInstitutionCode, String challengeReference) {
        return ResponseEntity.ok(mapper.toChallengeResult(
                useCase.get(xFinancialInstitutionCode, challengeReference)));
    }

    @Override
    public ResponseEntity<PaymentConfirmationChallengeResult> recoverPaymentConfirmationByIdempotencyKey(
            UUID xCorrelationID, String xFinancialInstitutionCode, String idempotencyKey) {
        return ResponseEntity.ok(mapper.toChallengeResult(
                useCase.recover(xFinancialInstitutionCode, idempotencyKey)));
    }

    @Override
    public ResponseEntity<PaymentConfirmationChallengeResult> revokePaymentConfirmationChallenge(
            UUID xCorrelationID, String xFinancialInstitutionCode, String idempotencyKey,
            String challengeReference, RevokePaymentConfirmationChallengeRequest request) {
        return ResponseEntity.ok(mapper.toChallengeResult(
                useCase.revoke(xFinancialInstitutionCode, idempotencyKey, challengeReference, mapper.toDomain(request))));
    }
}
