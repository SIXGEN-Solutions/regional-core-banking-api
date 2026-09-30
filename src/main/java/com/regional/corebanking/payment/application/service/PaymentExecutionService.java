package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.port.in.PaymentExecutionUseCase;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
import org.springframework.stereotype.Service;

@Service
public class PaymentExecutionService implements PaymentExecutionUseCase {

    private final PaymentExecutionBankingPort bankingPort;
    private final PaymentExecutionPersistencePort persistencePort;

    public PaymentExecutionService(
            PaymentExecutionBankingPort bankingPort,
            PaymentExecutionPersistencePort persistencePort) {
        this.bankingPort = bankingPort;
        this.persistencePort = persistencePort;
    }

    @Override
    public PaymentExecutionResult execute(
            String financialInstitutionCode,
            String idempotencyKey,
            PaymentExecutionCommand command) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(idempotencyKey, "idempotencyKey");
        validate(command);

        String fingerprint = PaymentExecutionFingerprint.sha256(command);
        return persistencePort.executeIdempotent(
                financialInstitutionCode,
                idempotencyKey,
                fingerprint,
                command,
                () -> bankingPort.execute(financialInstitutionCode, idempotencyKey, command));
    }

    @Override
    public PaymentExecutionResult recoverByPaymentReference(
            String financialInstitutionCode,
            String paymentReference) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(paymentReference, "paymentReference");

        return persistencePort.findByPaymentReference(financialInstitutionCode, paymentReference)
                .or(() -> bankingPort.findByPaymentReference(financialInstitutionCode, paymentReference))
                .orElseThrow(() -> new PaymentExecutionNotFoundException(
                        "No authoritative payment execution result for paymentReference"));
    }

    @Override
    public PaymentExecutionResult recoverByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(idempotencyKey, "idempotencyKey");

        return persistencePort.findByIdempotencyKey(financialInstitutionCode, idempotencyKey)
                .or(() -> bankingPort.findByIdempotencyKey(financialInstitutionCode, idempotencyKey))
                .orElseThrow(() -> new PaymentExecutionNotFoundException(
                        "No authoritative payment execution result for idempotencyKey"));
    }

    private static void validate(PaymentExecutionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("payment execution command is required");
        }
        requireText(command.paymentReference(), "paymentReference");
        requireText(command.snapshotVersion(), "snapshotVersion");
        if (command.providerEvent() == null) {
            throw new IllegalArgumentException("providerEvent is required");
        }
        if (!command.paymentReference().equals(command.providerEvent().paymentReference())) {
            throw new IllegalArgumentException("providerEvent.paymentReference must match envelope paymentReference");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
