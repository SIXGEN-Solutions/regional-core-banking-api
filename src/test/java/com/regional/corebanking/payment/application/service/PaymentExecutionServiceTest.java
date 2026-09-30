package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutionServiceTest {

    @Test
    void delegatesExecutionThroughRegionalIdempotencyPersistence() {
        var banking = new FakeBankingPort();
        var persistence = new FakePersistencePort();
        var service = new PaymentExecutionService(banking, persistence);

        PaymentExecutionResult result = service.execute("REGIONAL", "idem-123456789012", command());

        assertEquals(PaymentExecutionOutcome.COMPLETED, result.outcome());
        assertEquals(1, banking.executeCalls);
        assertNotNull(persistence.lastFingerprint);
        assertEquals(64, persistence.lastFingerprint.length());
    }

    @Test
    void recoveryUsesRegionalPersistenceBeforeBankingLookup() {
        var banking = new FakeBankingPort();
        var persistence = new FakePersistencePort();
        persistence.stored = Optional.of(completed());
        var service = new PaymentExecutionService(banking, persistence);

        assertEquals("BANK-1", service.recoverByPaymentReference("REGIONAL", "PAY-1").bankReference());
        assertEquals(0, banking.paymentLookupCalls);
    }

    @Test
    void recoveryFallsBackToAuthoritativeBankingLookupWithoutExecuting() {
        var banking = new FakeBankingPort();
        var persistence = new FakePersistencePort();
        var service = new PaymentExecutionService(banking, persistence);

        assertEquals("BANK-1", service.recoverByIdempotencyKey("REGIONAL", "idem-123456789012").bankReference());
        assertEquals(0, banking.executeCalls);
        assertEquals(1, banking.idempotencyLookupCalls);
    }

    @Test
    void missingRecoveryNeverTriggersExecution() {
        PaymentExecutionBankingPort banking = new PaymentExecutionBankingPort() {
            @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand command) {
                fail("execute must not be called by recovery"); return null;
            }
            @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String reference) { return Optional.empty(); }
            @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) { return Optional.empty(); }
        };
        var service = new PaymentExecutionService(banking, new FakePersistencePort());

        assertThrows(PaymentExecutionNotFoundException.class,
                () -> service.recoverByIdempotencyKey("REGIONAL", "missing"));
    }

    @Test
    void fingerprintIsStableForEquivalentAmounts() {
        var first = command();
        var event = first.providerEvent();
        var equivalent = new PaymentExecutionCommand(
                first.paymentReference(),
                first.snapshotVersion(),
                new PaymentProviderEvent(
                        event.paymentReference(), event.operationCode(), event.currency(), event.nature(),
                        event.technicalUser(), event.debtorAccountReference(), event.creditorAccountReference(),
                        new BigDecimal("1000.00"), event.label()),
                first.requestedAt());

        assertEquals(PaymentExecutionFingerprint.sha256(first), PaymentExecutionFingerprint.sha256(equivalent));
    }

    @Test
    void rejectsEnvelopeWhosePaymentReferenceDiffersFromProviderPayload() {
        var base = command();
        var badEvent = new PaymentProviderEvent(
                "OTHER", "001", "XAF", "PAYMENT", "SYSTEM",
                "00001-12345678901-42", "00001-12345678902-40",
                new BigDecimal("1000"), "Payment");
        var invalid = new PaymentExecutionCommand(
                base.paymentReference(), base.snapshotVersion(), badEvent, base.requestedAt());

        var service = new PaymentExecutionService(new FakeBankingPort(), new FakePersistencePort());
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("REGIONAL", "idem-123456789012", invalid));
    }

    private static PaymentExecutionCommand command() {
        String paymentReference = "PAY-1";
        return new PaymentExecutionCommand(
                paymentReference,
                "v1",
                new PaymentProviderEvent(
                        paymentReference, "001", "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        new BigDecimal("1000"), "Payment"),
                OffsetDateTime.parse("2026-09-28T20:00:00-04:00"));
    }

    private static PaymentExecutionResult completed() {
        List<PaymentExecutionCheck> checks = List.of(
                check(PaymentExecutionCheckType.ACCOUNT_VALID),
                check(PaymentExecutionCheckType.DEBIT_ALLOWED),
                check(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT));
        return new PaymentExecutionResult(
                "PAY-1", PaymentExecutionOutcome.COMPLETED, checks,
                "BANK-1", null, OffsetDateTime.parse("2026-09-28T20:00:01-04:00"));
    }

    private static PaymentExecutionCheck check(PaymentExecutionCheckType type) {
        return new PaymentExecutionCheck(type, PaymentExecutionCheckResult.PASS, null);
    }

    private static final class FakePersistencePort implements PaymentExecutionPersistencePort {
        private Optional<PaymentExecutionResult> stored = Optional.empty();
        private String lastFingerprint;

        @Override
        public PaymentExecutionResult executeIdempotent(
                String institution, String key, String fingerprint,
                PaymentExecutionCommand command, Supplier<PaymentExecutionResult> execution) {
            lastFingerprint = fingerprint;
            if (stored.isPresent()) return stored.orElseThrow();
            PaymentExecutionResult result = execution.get();
            stored = Optional.of(result);
            return result;
        }

        @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String reference) { return stored; }
        @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) { return stored; }
    }

    private static final class FakeBankingPort implements PaymentExecutionBankingPort {
        private int executeCalls;
        private int paymentLookupCalls;
        private int idempotencyLookupCalls;

        @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand command) {
            executeCalls++; return completed();
        }
        @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String reference) {
            paymentLookupCalls++; return "PAY-1".equals(reference) ? Optional.of(completed()) : Optional.empty();
        }
        @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
            idempotencyLookupCalls++; return "idem-123456789012".equals(key) ? Optional.of(completed()) : Optional.empty();
        }
    }
}
