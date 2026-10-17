package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BkeveEventNumberAllocationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    @Test
    void concurrentCallersReceiveDistinctNumbersFromAnAtomicTestPort() throws Exception {
        // TEST ONLY: this JVM-local counter is not a real banking allocator.
        AtomicInteger counter = new AtomicInteger();
        BkeveAtomicEventNumberPort testPort = (operation, table, date) ->
                "%06d".formatted(counter.incrementAndGet());
        var service = new BkeveEventNumberAllocationService(testPort);
        int count = 256;
        var pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                calls.add(() -> service.allocate("001", "BKEVE", DATE));
            }
            List<Future<String>> futures = pool.invokeAll(calls);
            Set<String> allocated = new HashSet<>();
            for (Future<String> future : futures) {
                allocated.add(future.get());
            }
            assertEquals(count, allocated.size());
            assertEquals(count, counter.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidInputBeforeConsultingBank() {
        AtomicInteger invocations = new AtomicInteger();
        BkeveAtomicEventNumberPort port = (operation, table, date) -> {
            invocations.incrementAndGet();
            return "000001";
        };
        var service = new BkeveEventNumberAllocationService(port);
        assertThrows(IllegalArgumentException.class,
                () -> service.allocate("", "BKEVE", DATE));
        assertThrows(IllegalArgumentException.class,
                () -> service.allocate("001", "OTHER", DATE));
        assertThrows(NullPointerException.class,
                () -> service.allocate("001", "BKEVE", null));
        assertEquals(0, invocations.get());
    }

    @Test
    void rejectsMalformedNumbersAndNeverInventsAFallback() {
        for (String invalid : new String[]{"", "12", "1234567", "ABC123"}) {
            var service = new BkeveEventNumberAllocationService((operation, table, date) -> invalid);
            assertThrows(IllegalStateException.class,
                    () -> service.allocate("001", "BKEVE", DATE));
        }
        var nullService = new BkeveEventNumberAllocationService((operation, table, date) -> null);
        assertThrows(IllegalStateException.class,
                () -> nullService.allocate("001", "BKEVE", DATE));
    }

    @Test
    void productionPlaceholderAlwaysFailsClosed() {
        var service = new BkeveEventNumberAllocationService(new UnconfiguredBkeveAtomicEventNumberPort());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.allocate("001", "BKEVE", DATE));
    }
}
