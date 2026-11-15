package com.regional.corebanking.payment.application.port.out;

import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

import java.util.Optional;
import java.util.function.Supplier;

public interface PaymentExecutionPersistencePort {

    PaymentExecutionResult executeIdempotent(
            String financialInstitutionCode,
            String idempotencyKey,
            String requestFingerprint,
            PaymentExecutionCommand command,
            Supplier<PaymentExecutionResult> execution);

    Optional<PaymentExecutionResult> findByPaymentReference(
            String financialInstitutionCode,
            String paymentReference);

    Optional<PaymentExecutionResult> findByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey);
}
