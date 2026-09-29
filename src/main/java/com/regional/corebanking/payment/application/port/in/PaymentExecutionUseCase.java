package com.regional.corebanking.payment.application.port.in;

import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

public interface PaymentExecutionUseCase {
    PaymentExecutionResult execute(
            String financialInstitutionCode,
            String idempotencyKey,
            PaymentExecutionCommand command);

    PaymentExecutionResult recoverByPaymentReference(
            String financialInstitutionCode,
            String paymentReference);

    PaymentExecutionResult recoverByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey);
}
