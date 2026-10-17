package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mock-JDBC transaction tests; they do NOT validate Informix balance SQL. */
class BkevePostingAndBalanceWriteTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    private static BkeveJdbcParameterMapper.Parameters values() {
        return new BkeveJdbcParameterMapper.Parameters(
                BkeveJdbcColumnOrder.COLUMNS, Collections.nCopies(233, null));
    }

    @Test void insertAndBankBalanceUpdateShareOneConnectionBeforeCommit() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(connection);
        AtomicBoolean autoCommit = new AtomicBoolean(true);

        when(connection.getAutoCommit())
                .thenAnswer(invocation -> autoCommit.get());

        doAnswer(invocation -> {
            autoCommit.set(invocation.getArgument(0, Boolean.class));
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        when(connection.prepareStatement("bank-day-insert")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        List<String> order = new ArrayList<>();
        BkeveIndicativeBalanceUpdate bankStub = (cx, table, eve) -> {
            assertSame(connection, cx);
            assertEquals("BKEVE", table);
            assertEquals("000421", eve);
            order.add("balance");
        };
        var writer = new BkevePostingAndBalanceWrite(
                new BkeveJdbcPostingAdapter(table -> "bank-day-insert", new BkeveJdbcParameterMapper()),
                bankStub);
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> {
            assertSame(connection, cx);
            order.add("allocated");
            return "000421";
        });
        // Track INSERT via JDBC mock, without invoking any real bank SQL.
        when(statement.executeUpdate()).thenAnswer(invocation -> { order.add("insert"); return 1; });
        doAnswer(invocation -> { order.add("commit"); return null; }).when(connection).commit();
        coordinator.allocateAndWrite("001", "BKEVE", DATE, (cx, eve) -> {
            writer.execute(cx, "BKEVE", eve, values());
            return eve;
        });
        assertEquals(List.of("allocated", "insert", "balance", "commit"), order);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test void failedBankBalanceUpdateRollsBackBkeveInsertWithoutRetry() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(connection);
        AtomicBoolean autoCommit = new AtomicBoolean(true);

        when(connection.getAutoCommit())
                .thenAnswer(invocation -> autoCommit.get());

        doAnswer(invocation -> {
            autoCommit.set(invocation.getArgument(0, Boolean.class));
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        when(connection.prepareStatement("bank-night-insert")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        BkeveIndicativeBalanceUpdate fails = (cx, table, eve) -> {
            assertSame(connection, cx);
            assertEquals("BKEVE_EOD", table);
            throw new SQLException("bank balance update rejected");
        };
        var writer = new BkevePostingAndBalanceWrite(
                new BkeveJdbcPostingAdapter(table -> "bank-night-insert", new BkeveJdbcParameterMapper()),
                fails);
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> "000422");
        assertThrows(RuntimeException.class, () -> coordinator.allocateAndWrite("001", "BKEVE_EOD", DATE,
                (cx, eve) -> { writer.execute(cx, "BKEVE_EOD", eve, values()); return eve; }));
        verify(statement, times(1)).executeUpdate();
        verify(connection, times(1)).rollback();
        verify(connection, never()).commit();
    }

    @Test void unconfiguredBalanceStrategyRefusesCommitAndRollsBack() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(connection);
        AtomicBoolean autoCommit = new AtomicBoolean(true);

        when(connection.getAutoCommit())
                .thenAnswer(invocation -> autoCommit.get());

        doAnswer(invocation -> {
            autoCommit.set(invocation.getArgument(0, Boolean.class));
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        when(connection.prepareStatement("bank-insert-stub")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        var writer = new BkevePostingAndBalanceWrite(
                new BkeveJdbcPostingAdapter(table -> "bank-insert-stub", new BkeveJdbcParameterMapper()),
                BkeveIndicativeBalanceUpdate.unconfigured());
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (cx, op, table, date) -> "000423");
        assertThrows(RuntimeException.class, () -> coordinator.allocateAndWrite("001", "BKEVE", DATE,
                (cx, eve) -> { writer.execute(cx, "BKEVE", eve, values()); return eve; }));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
