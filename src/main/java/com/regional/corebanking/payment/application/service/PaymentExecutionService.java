package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.port.in.PaymentExecutionUseCase;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
import com.regional.corebanking.payment.domain.PaymentProviderEntry;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class PaymentExecutionService implements PaymentExecutionUseCase {

    private final PaymentExecutionBankingPort bankingPort;

    public PaymentExecutionService(PaymentExecutionBankingPort bankingPort) {
        this.bankingPort = bankingPort;
    }

    @Override
    public PaymentExecutionResult execute(
            String financialInstitutionCode,
            String idempotencyKey,
            PaymentExecutionCommand command) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(idempotencyKey, "idempotencyKey");
        validate(command);

        /*
         * Financial idempotency is deliberately enforced at the authoritative
         * banking boundary. The Regional application must never retry a command
         * whose outcome may be unknown without first using one of the recovery
         * operations below.
         */
        return bankingPort.execute(financialInstitutionCode, idempotencyKey, command);
    }

    @Override
    public PaymentExecutionResult recoverByPaymentReference(
            String financialInstitutionCode,
            String paymentReference) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(paymentReference, "paymentReference");
        return bankingPort.findByPaymentReference(financialInstitutionCode, paymentReference)
                .orElseThrow(() -> new PaymentExecutionNotFoundException(
                        "No authoritative payment execution result for paymentReference"));
    }

    @Override
    public PaymentExecutionResult recoverByIdempotencyKey(
            String financialInstitutionCode,
            String idempotencyKey) {
        requireText(financialInstitutionCode, "financialInstitutionCode");
        requireText(idempotencyKey, "idempotencyKey");
        return bankingPort.findByIdempotencyKey(financialInstitutionCode, idempotencyKey)
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
        if (command.providerEntries() == null || command.providerEntries().size() != 2) {
            throw new IllegalArgumentException("providerEntries must contain exactly two entries");
        }

        Set<Integer> sequences = new HashSet<>();
        for (PaymentProviderEntry entry : command.providerEntries()) {
            if (entry == null) {
                throw new IllegalArgumentException("providerEntries must not contain null");
            }
            if (!command.paymentReference().equals(entry.paymentReference())) {
                throw new IllegalArgumentException("entry paymentReference must match envelope paymentReference");
            }
            if (!"D".equals(entry.direction()) && !"C".equals(entry.direction())) {
                throw new IllegalArgumentException("entry direction must be D or C");
            }
            sequences.add(entry.sequence());
        }
        if (sequences.size() != 2) {
            throw new IllegalArgumentException("provider entry sequences must be distinct");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
