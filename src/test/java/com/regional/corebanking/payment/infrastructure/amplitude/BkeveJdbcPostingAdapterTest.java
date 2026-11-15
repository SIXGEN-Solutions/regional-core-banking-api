package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JDBC mock contract tests; NOT Informix integration tests. */
class BkeveJdbcPostingAdapterTest {
    private static BkeveJdbcParameterMapper.Parameters parameters() {
        return new BkeveJdbcParameterMapper.Parameters(
                BkeveJdbcColumnOrder.COLUMNS,
                Collections.nCopies(233, null));
    }

    @Test void dayInsertBindsAll233PositionsWithoutOwningCommit() throws Exception {
        var c = mock(Connection.class);
        var stmt = mock(PreparedStatement.class);
        when(c.getAutoCommit()).thenReturn(false);
        when(c.prepareStatement("approved-day-sql")).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        var writer = new BkeveJdbcPostingAdapter(table -> "BKEVE".equals(table)
                ? "approved-day-sql" : null, new BkeveJdbcParameterMapper());
        writer.insert(c, "BKEVE", parameters());
        verify(c).prepareStatement("approved-day-sql");
        verify(stmt, times(233)).setNull(anyInt(), anyInt());
        verify(stmt).executeUpdate();
        verify(stmt).close();
        verify(c, never()).commit();
        verify(c, never()).rollback();
        verify(c, never()).close();
    }

    @Test void nightModeUsesOnlyTheSelectedBankApprovedStatement() throws Exception {
        var c = mock(Connection.class);
        var stmt = mock(PreparedStatement.class);
        when(c.getAutoCommit()).thenReturn(false);
        when(c.prepareStatement("approved-night-sql")).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);
        var calls = new AtomicInteger();
        var writer = new BkeveJdbcPostingAdapter(table -> {
            calls.incrementAndGet();
            return switch (table) {
                case "BKEVE" -> "approved-day-sql";
                case "BKEVE_EOD" -> "approved-night-sql";
                default -> null;
            };
        }, new BkeveJdbcParameterMapper());
        writer.insert(c, "BKEVE_EOD", parameters());
        assertEquals(1, calls.get());
        verify(c).prepareStatement("approved-night-sql");
        verify(c, never()).prepareStatement("approved-day-sql");
    }

    @Test void missingApprovalRefusesAnySqlExecution() throws Exception {
        var c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        var writer = new BkeveJdbcPostingAdapter(table -> null, new BkeveJdbcParameterMapper());
        assertThrows(IllegalStateException.class, () -> writer.insert(c, "BKEVE", parameters()));
        verify(c, never()).prepareStatement(anyString());
    }

    @Test void invalidTableOrAutocommitRefusesPosting() throws Exception {
        var c = mock(Connection.class);
        var writer = new BkeveJdbcPostingAdapter(table -> "approved-sql", new BkeveJdbcParameterMapper());
        assertThrows(IllegalArgumentException.class, () -> writer.insert(c, "BKEVE;DROP", parameters()));
        when(c.getAutoCommit()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> writer.insert(c, "BKEVE", parameters()));
        verify(c, never()).prepareStatement(anyString());
    }

    @Test void unexpectedRowCountIsFailureWithoutRetryOrCommit() throws Exception {
        var c = mock(Connection.class);
        var stmt = mock(PreparedStatement.class);
        when(c.getAutoCommit()).thenReturn(false);
        when(c.prepareStatement("approved-sql")).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);
        var writer = new BkeveJdbcPostingAdapter(table -> "approved-sql", new BkeveJdbcParameterMapper());
        assertThrows(SQLException.class, () -> writer.insert(c, "BKEVE", parameters()));
        verify(stmt, times(1)).executeUpdate();
        verify(c, never()).commit();
        verify(c, never()).rollback();
    }
}
