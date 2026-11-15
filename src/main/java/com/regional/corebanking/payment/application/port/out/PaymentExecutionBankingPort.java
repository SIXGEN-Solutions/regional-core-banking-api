package com.regional.corebanking.payment.application.port.out;

import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

import java.util.Optional;

public interface PaymentExecutionBankingPort {
    PaymentExecutionResult execute(
            String financialInstitutionCode,
            String idempotencyKey,
            PaymentExecutionCommand command);

    Optional<PaymentExecutionResult> findByPaymentReference(
            String financialInstitutionCode,
            String paymentReference);

    Optional<PaymentExecutionResult> findByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey);
}
