package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcBkeveTransactionCoordinatorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    @Test void allocationAndWriteShareOneJdbcConnectionAndCommitOnce() throws Exception {
        var ds = mock(DataSource.class);
        var c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.getAutoCommit()).thenReturn(true);
        var allocations = new AtomicInteger();
        var writes = new AtomicInteger();
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (connection, op, table, date) -> {
            assertSame(c, connection);
            allocations.incrementAndGet();
            return "000124";
        });
        var result = coordinator.allocateAndWrite("001", "BKEVE", DATE, (connection, eve) -> {
            assertSame(c, connection);
            assertEquals("000124", eve);
            writes.incrementAndGet();
            return eve;
        });
        assertEquals("000124", result);
        assertEquals(1, allocations.get());
        assertEquals(1, writes.get());
        var order = inOrder(c);
        order.verify(c).setAutoCommit(false);
        order.verify(c).commit();
        order.verify(c).close();
        verify(c, never()).rollback();
    }

    @Test void writeFailureRollsBackAndNeverRetries() throws Exception {
        var ds = mock(DataSource.class); var c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.getAutoCommit()).thenReturn(true);
        var attempts = new AtomicInteger();
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> "000125");
        assertThrows(RuntimeException.class, () -> coordinator.allocateAndWrite("001", "BKEVE", DATE, (cx, eve) -> {
            attempts.incrementAndGet(); throw new SQLException("write failed");
        }));
        assertEquals(1, attempts.get());
        verify(c).rollback();
        verify(c, never()).commit();
    }

    @Test void uncertainCommitIsNotRetried() throws Exception {
        var ds = mock(DataSource.class); var c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("connection lost on commit")).when(c).commit();
        var writes = new AtomicInteger();
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> "000126");
        var failure = assertThrows(RuntimeException.class,
            () -> coordinator.allocateAndWrite("001", "BKEVE", DATE, (cx, eve) -> {writes.incrementAndGet();return eve;}));
        assertTrue(failure.getMessage().contains("uncertain"));
        assertEquals(1, writes.get());
        verify(c, never()).rollback();
    }

    @Test void refusesUnknownBankingAllocatorAndBadNumbers() throws Exception {
        var ds = mock(DataSource.class); var c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.getAutoCommit()).thenReturn(true);
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> "000000");
        var writes = new AtomicInteger();
        assertThrows(RuntimeException.class,
            () -> coordinator.allocateAndWrite("001", "BKEVE", DATE, (cx, eve) -> {writes.incrementAndGet();return eve;}));
        assertEquals(0, writes.get());
        verify(c).rollback();
    }

    @Test void distinctCoordinatorsSerializeWholeTransactionWithinSingleJvm() throws Exception {
        var ds = mock(DataSource.class);
        AtomicInteger currentlyInTransaction = new AtomicInteger();
        AtomicInteger maximumConcurrent = new AtomicInteger();
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        when(ds.getConnection()).thenAnswer(invocation -> {
            Connection c = mock(Connection.class);
            when(c.getAutoCommit()).thenReturn(true);
            doAnswer(x -> {
                commits.incrementAndGet();
                currentlyInTransaction.decrementAndGet();
                return null;
            }).when(c).commit();
            return c;
        });
        JdbcBkeveTransactionCoordinator.LockedAllocator testOnly = (connection, op, table, date) -> {
            int active = currentlyInTransaction.incrementAndGet();
            maximumConcurrent.accumulateAndGet(active, Math::max);
            // Widen the race window without relying on the ordering of threads.
            Thread.sleep(2);
            return "%06d".formatted(sequence.incrementAndGet());
        };
        var instanceA = new JdbcBkeveTransactionCoordinator(ds, testOnly);
        var instanceB = new JdbcBkeveTransactionCoordinator(ds, testOnly);
        var workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> operations = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                var coordinator = i % 2 == 0 ? instanceA : instanceB;
                operations.add(() -> coordinator.allocateAndWrite("001", "BKEVE", DATE,
                        (connection, eve) -> eve));
            }
            List<Future<String>> results = workers.invokeAll(operations);
            var numbers = new HashSet<String>();
            for (var f : results) numbers.add(f.get());
            assertEquals(64, numbers.size());
            assertEquals(64, commits.get());
            assertEquals(0, currentlyInTransaction.get());
            assertEquals(1, maximumConcurrent.get());
        } finally {
            workers.shutdownNow();
        }
    }
}
