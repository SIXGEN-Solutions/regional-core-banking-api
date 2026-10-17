package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
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
}
