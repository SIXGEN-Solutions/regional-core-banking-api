package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutionServiceTest {

    @Test
    void delegatesOneFinancialExecutionWithoutApplicationReplay() {
        var port = new FakeBankingPort();
        var service = new PaymentExecutionService(port);
        PaymentExecutionResult result = service.execute("REGIONAL", "idem-123456789012", command());
        assertEquals(PaymentExecutionOutcome.COMPLETED, result.outcome());
        assertEquals(1, port.executeCalls);
    }

    @Test
    void recoversAuthoritativelyByPaymentReferenceAndIdempotencyKey() {
        var port = new FakeBankingPort();
        var service = new PaymentExecutionService(port);
        assertEquals("BANK-1", service.recoverByPaymentReference("REGIONAL", "PAY-1").bankReference());
        assertEquals("BANK-1", service.recoverByIdempotencyKey("REGIONAL", "idem-123456789012").bankReference());
    }

    @Test
    void doesNotTurnMissingAuthoritativeRecoveryIntoAReplay() {
        PaymentExecutionBankingPort port = new PaymentExecutionBankingPort() {
            @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand command) {
                fail("execute must not be called by recovery"); return null;
            }
            @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String paymentReference) { return Optional.empty(); }
            @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) { return Optional.empty(); }
        };
        var service = new PaymentExecutionService(port);
        assertThrows(PaymentExecutionNotFoundException.class,
                () -> service.recoverByIdempotencyKey("REGIONAL", "idem-123456789012"));
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
        var service = new PaymentExecutionService(new FakeBankingPort());
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("REGIONAL", "idem-123456789012", invalid));
    }

    private static PaymentExecutionCommand command() {
        String paymentReference = "PAY-1";
        BigDecimal amount = new BigDecimal("1000");
        return new PaymentExecutionCommand(
                paymentReference,
                "v1",
                new PaymentProviderEvent(
                        paymentReference, "001", "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        amount, "Payment"),
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

    private static final class FakeBankingPort implements PaymentExecutionBankingPort {
        private int executeCalls;
        @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand command) { executeCalls++; return completed(); }
        @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String paymentReference) { return "PAY-1".equals(paymentReference) ? Optional.of(completed()) : Optional.empty(); }
        @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) { return "idem-123456789012".equals(key) ? Optional.of(completed()) : Optional.empty(); }
    }
}
