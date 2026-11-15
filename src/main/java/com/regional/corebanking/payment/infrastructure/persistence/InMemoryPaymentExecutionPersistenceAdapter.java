package com.regional.corebanking.payment.infrastructure.persistence;

import com.regional.corebanking.payment.application.exception.PaymentExecutionIdempotencyConflictException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InMemoryPaymentExecutionPersistenceAdapter implements PaymentExecutionPersistencePort {

    private final Map<ExecutionKey, StoredExecution> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<PaymentKey, StoredExecution> byPaymentReference = new ConcurrentHashMap<>();

    @Override
    public PaymentExecutionResult executeIdempotent(
            String financialInstitutionCode,
            String idempotencyKey,
            String requestFingerprint,
            PaymentExecutionCommand command,
            Supplier<PaymentExecutionResult> execution) {

        ExecutionKey executionKey = new ExecutionKey(financialInstitutionCode, idempotencyKey);
        StoredExecution candidate = new StoredExecution(requestFingerprint);
        StoredExecution stored = byIdempotencyKey.putIfAbsent(executionKey, candidate);

        if (stored == null) {
            stored = candidate;
            byPaymentReference.put(
                    new PaymentKey(financialInstitutionCode, command.paymentReference()),
                    stored);
        }

        synchronized (stored) {
            if (!stored.requestFingerprint.equals(requestFingerprint)) {
                throw new PaymentExecutionIdempotencyConflictException(
                        "Idempotency key is already associated with a different Payment Execution request");
            }
            if (stored.result != null) {
                return stored.result;
            }
            if (stored.executionAttempted) {
                throw unresolved();
            }

            stored.executionAttempted = true;
            try {
                stored.result = execution.get();
                return stored.result;
            } catch (RuntimeException exception) {
                // Preserve the claim after an uncertain failure. A later request must
                // recover instead of invoking the financial execution again.
                throw exception;
            }
        }
    }

    @Override
    public Optional<PaymentExecutionResult> findByPaymentReference(
            String financialInstitutionCode,
            String paymentReference) {
        StoredExecution stored = byPaymentReference.get(
                new PaymentKey(financialInstitutionCode, paymentReference));
        return stored == null ? Optional.empty() : Optional.ofNullable(stored.result);
    }

    @Override
    public Optional<PaymentExecutionResult> findByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey) {
        StoredExecution stored = byIdempotencyKey.get(
                new ExecutionKey(financialInstitutionCode, idempotencyKey));
        return stored == null ? Optional.empty() : Optional.ofNullable(stored.result);
    }

    private static PaymentExecutionUnavailableException unresolved() {
        return new PaymentExecutionUnavailableException(
                "Payment Execution outcome is unresolved; authoritative recovery is required before any replay");
    }

    private static final class StoredExecution {
        private final String requestFingerprint;
        private boolean executionAttempted;
        private PaymentExecutionResult result;

        private StoredExecution(String requestFingerprint) {
            this.requestFingerprint = requestFingerprint;
        }
    }

    private record ExecutionKey(String financialInstitutionCode, String idempotencyKey) {
    }

    private record PaymentKey(String financialInstitutionCode, String paymentReference) {
    }
}
