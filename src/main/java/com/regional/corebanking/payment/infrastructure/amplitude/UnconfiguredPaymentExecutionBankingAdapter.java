package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

import java.util.Optional;

/**
 * Fail-closed placeholder for the physical Amplitude/Informix payment execution adapter.
 *
 * The repository currently has no approved La Régionale evidence defining the exact
 * SQL/procedure, transaction boundary, execution checks, bank-reference allocation or
 * authoritative lookup mechanism. Inventing any of those would be unsafe for a
 * financial operation. Replace this adapter only when that bank evidence is approved.
 */
public class UnconfiguredPaymentExecutionBankingAdapter implements PaymentExecutionBankingPort {

    private static PaymentExecutionUnavailableException unavailable() {
        return new PaymentExecutionUnavailableException(
                "Payment execution banking adapter is not configured: approved La Régionale execution/recovery mapping is required");
    }

    @Override
    public PaymentExecutionResult execute(
            String financialInstitutionCode,
            String idempotencyKey,
            PaymentExecutionCommand command) {
        throw unavailable();
    }

    @Override
    public Optional<PaymentExecutionResult> findByPaymentReference(
            String financialInstitutionCode,
            String paymentReference) {
        throw unavailable();
    }

    @Override
    public Optional<PaymentExecutionResult> findByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey) {
        throw unavailable();
    }
}
