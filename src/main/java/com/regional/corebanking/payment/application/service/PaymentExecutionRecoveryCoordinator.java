package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.PaymentExecutionOutcome;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only financial recovery coordination. Never invokes financial execution.
 * BankingPort lookups MUST be authoritative before they can resolve UNKNOWN.
 * A missing bank row does not prove that no financial posting occurred.
 */
final class PaymentExecutionRecoveryCoordinator {
    private final PaymentExecutionPersistencePort technical;
    private final PaymentExecutionBankingPort banking;

    PaymentExecutionRecoveryCoordinator(PaymentExecutionPersistencePort technical,
                                        PaymentExecutionBankingPort banking) {
        this.technical = Objects.requireNonNull(technical, "technical");
        this.banking = Objects.requireNonNull(banking, "banking");
    }

    PaymentExecutionResult byPaymentReference(String institution, String reference) {
        require(institution, "financialInstitutionCode");
        require(reference, "paymentReference");
        Optional<PaymentExecutionResult> local = technical.findByPaymentReference(institution, reference);
        if (local.isPresent() && local.get().outcome() != PaymentExecutionOutcome.UNKNOWN) {
            validateReference(local.get(), reference);
            return local.get();
        }
        Optional<PaymentExecutionResult> bank = banking.findByPaymentReference(institution, reference);
        if (bank.isPresent()) {
            validateReference(bank.get(), reference);
            return validateBankOutcome(bank.get());
        }
        return local.orElseThrow(() -> new PaymentExecutionNotFoundException(
                "No authoritative payment execution result for paymentReference"));
    }

    PaymentExecutionResult byIdempotencyKey(String institution, String key) {
        require(institution, "financialInstitutionCode");
        require(key, "idempotencyKey");
        Optional<PaymentExecutionResult> local = technical.findByIdempotencyKey(institution, key);
        if (local.isPresent() && local.get().outcome() != PaymentExecutionOutcome.UNKNOWN) {
            return local.get();
        }
        Optional<PaymentExecutionResult> bank = banking.findByIdempotencyKey(institution, key);
        if (bank.isPresent()) {
            PaymentExecutionResult candidate = validateBankOutcome(bank.get());
            if (local.isPresent() && !Objects.equals(local.get().paymentReference(), candidate.paymentReference())) {
                throw new PaymentExecutionUnavailableException("Bank recovery payment reference does not match technical claim");
            }
            return candidate;
        }
        return local.orElseThrow(() -> new PaymentExecutionNotFoundException(
                "No authoritative payment execution result for idempotencyKey"));
    }

    private static PaymentExecutionResult validateBankOutcome(PaymentExecutionResult result) {
        if (result.outcome() == null || result.outcome() == PaymentExecutionOutcome.UNKNOWN) {
            // An unresolved bank lookup cannot upgrade a technical UNKNOWN.
            throw new PaymentExecutionUnavailableException("Bank recovery outcome remains uncertain");
        }
        if (result.outcome() == PaymentExecutionOutcome.COMPLETED &&
                (result.bankReference() == null || result.bankReference().isBlank())) {
            throw new PaymentExecutionUnavailableException("Completed bank recovery lacks bank reference");
        }
        return result;
    }

    private static void validateReference(PaymentExecutionResult result, String reference) {
        if (!reference.equals(result.paymentReference())) {
            throw new PaymentExecutionUnavailableException("Bank recovery payment reference mismatch");
        }
    }

    private static void require(String text, String field) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
