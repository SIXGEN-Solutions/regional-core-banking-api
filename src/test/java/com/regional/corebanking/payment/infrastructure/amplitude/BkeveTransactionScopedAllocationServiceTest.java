package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BkeveTransactionScopedAllocationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    /** Simulates two API instances using the SAME bank-coordinated scope. */
    @Test void parallelInstancesUseExclusiveScopeForAllocationAndWrite() throws Exception {
        Object sharedBankScope = new Object(); // test only, NOT a production lock
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        BkeveTransactionScopedAllocationPort bankSimulation = new BkeveTransactionScopedAllocationPort() {
            @Override public <T> T allocateAndWrite(String op, String table, LocalDate date,
                                                      FinancialWrite<T> callback) throws Exception {
                synchronized (sharedBankScope) {
                    String eve = "%06d".formatted(sequence.incrementAndGet());
                    T result = callback.execute(eve);
                    writes.incrementAndGet();
                    return result;
                }
            }
        };
        var instanceA = new BkeveTransactionScopedAllocationService(bankSimulation);
        var instanceB = new BkeveTransactionScopedAllocationService(bankSimulation);
        var executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 256; i++) {
                var service = i % 2 == 0 ? instanceA : instanceB;
                calls.add(() -> service.execute("001", "BKEVE", DATE, eve -> eve));
            }
            List<Future<String>> responses = executor.invokeAll(calls);
            var unique = new HashSet<String>();
            for (var response : responses) unique.add(response.get());
            assertEquals(256, unique.size());
            assertEquals(256, writes.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void invalidAllocatedNumberNeverReachesFinancialWrite() {
        AtomicInteger writes = new AtomicInteger();
        BkeveTransactionScopedAllocationPort bad = new BkeveTransactionScopedAllocationPort() {
            @Override public <T> T allocateAndWrite(String op, String table, LocalDate date,
                                                      FinancialWrite<T> callback) throws Exception {
                return callback.execute("000000");
            }
        };
        var service = new BkeveTransactionScopedAllocationService(bad);
        assertThrows(IllegalStateException.class,
                () -> service.execute("001", "BKEVE", DATE, eve -> { writes.incrementAndGet(); return eve; }));
        assertEquals(0, writes.get());
    }

    @Test void withoutApprovedBankImplementationAlwaysFailsClosed() {
        var service = new BkeveTransactionScopedAllocationService(
                new UnconfiguredBkeveTransactionScopedAllocationPort());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("001", "BKEVE", DATE, eve -> eve));
    }

    @Test void callbackIsNotRetriedByTheService() {
        AtomicInteger attempts = new AtomicInteger();
        BkeveTransactionScopedAllocationPort testPort = new BkeveTransactionScopedAllocationPort() {
            @Override public <T> T allocateAndWrite(String op, String table, LocalDate date,
                                                      FinancialWrite<T> callback) throws Exception {
                return callback.execute("000123");
            }
        };
        var service = new BkeveTransactionScopedAllocationService(testPort);
        assertThrows(IllegalStateException.class, () -> service.execute("001", "BKEVE", DATE,
                eve -> { attempts.incrementAndGet(); throw new IllegalStateException("unknown outcome"); }));
        assertEquals(1, attempts.get());
    }
}
