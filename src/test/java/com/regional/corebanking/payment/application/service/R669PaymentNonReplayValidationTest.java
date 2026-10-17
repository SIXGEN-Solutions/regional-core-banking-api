package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.*;
import com.regional.corebanking.payment.infrastructure.persistence.InMemoryPaymentExecutionPersistenceAdapter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** R6.6.9: JVM-only non-replay validation. NOT a PostgreSQL or Informix integration test. */
class R669PaymentNonReplayValidationTest {
    private static PaymentExecutionCommand command() {
        return new PaymentExecutionCommand("P-R669", "v1",
                new PaymentProviderEvent("P-R669", "001", "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        new BigDecimal("1000"), new BigDecimal("990"), "R669 test",
                        "00001", "00001", "test-marker"),
                OffsetDateTime.parse("2026-10-17T12:00:00Z"));
    }

    private static PaymentExecutionBankingPort failingBank(AtomicInteger attempts) {
        return new PaymentExecutionBankingPort() {
            @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand cmd) {
                attempts.incrementAndGet();
                throw new PaymentExecutionUnavailableException("Simulated uncertain banking outcome");
            }
            @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String ref) {
                return Optional.empty();
            }
            @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
                return Optional.empty();
            }
        };
    }

    @Test void uncertainExecutionIsNotInvokedTwiceForSameKey() {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669", command()));
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669", command()));
        assertEquals(1, attempts.get(), "An unresolved banking effect must not be replayed");
    }

    @Test void concurrentSameKeyDoesNotInvokeBankTwiceAfterUnknown() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 32; i++) {
                calls.add(() -> {
                    try { service.execute("REGIONAL", "idem-R669-concurrent", command()); return false; }
                    catch (PaymentExecutionUnavailableException expected) { return true; }
                });
            }
            for (Future<Boolean> result : workers.invokeAll(calls)) assertTrue(result.get());
            assertEquals(1, attempts.get(), "Concurrency must not duplicate a financial operation");
        } finally {
            workers.shutdownNow();
        }
    }

    @Test void unresolvedLookupCannotTriggerAnotherBankExecution() {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669-lookup", command()));
        // No authoritative bank record; technical claim still prevents retry.
        assertThrows(RuntimeException.class,
                () -> service.recoverByIdempotencyKey("REGIONAL", "idem-R669-lookup"));
        assertEquals(1, attempts.get());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669-lookup", command()));
        assertEquals(1, attempts.get());
    }
}
