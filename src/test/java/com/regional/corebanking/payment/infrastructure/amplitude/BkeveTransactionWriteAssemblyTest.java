package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mock JDBC only; not a bank-validated Informix integration test. */
class BkeveTransactionWriteAssemblyTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 17);
    private static final Set<String> RESERVED = Set.of(
            "AGSA", "AGE", "OPE", "MHT1", "MON1", "MHT2", "MON2", "LIB1", "CSP1",
            "EVE", "DCO", "DVA1", "DVA2");

    private static PaymentProviderEvent event() {
        return new PaymentProviderEvent("PAY-ASSEMBLY", "001", "XAF", "PAYMENT", "SYSTEM",
                "00001-12345678901-42", "00001-12345678902-40",
                new BigDecimal("1000"), new BigDecimal("990"), "Test",
                "00001", "00001", "TEST-MARKER");
    }

    private static PaymentExecutionBankingContext context(String eve, String table) {
        return new PaymentExecutionBankingContext(eve, DAY, "BKEVE_EOD".equals(table), table,
                DAY, DAY, "XAF");
    }

    private static Map<String, Object> suppliedBankValues() {
        var values = new HashMap<String, Object>();
        // Test fixtures with explicit NULLs prove mapping completeness only;
        // they are NOT approved values for a real Informix database.
        for (String column : BkeveJdbcColumnOrder.COLUMNS) {
            if (!RESERVED.contains(column)) values.put(column, null);
        }
        return values;
    }

    private static BkeveTransactionWriteAssembly assembly(
            BkeveJdbcPostingAdapter.BankApprovedInsertStatements sql,
            BkeveIndicativeBalanceUpdate balances) {
        return new BkeveTransactionWriteAssembly(
                new BkeveBankEnrichmentService(), new BkeveJdbcParameterMapper(),
                new BkevePostingAndBalanceWrite(
                        new BkeveJdbcPostingAdapter(sql, new BkeveJdbcParameterMapper()), balances));
    }

    private static Connection transactionMock() throws Exception {
        var connection = mock(Connection.class);
        var autoCommit = new AtomicBoolean(true);
        when(connection.getAutoCommit()).thenAnswer(i -> autoCommit.get());
        doAnswer(i -> { autoCommit.set(i.getArgument(0, Boolean.class)); return null; })
                .when(connection).setAutoCommit(anyBoolean());
        return connection;
    }

    @Test void dayWriteUsesSameConnectionAndCommitsOnlyAfterBalanceUpdate() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection cx = transactionMock();
        PreparedStatement statement = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(cx);
        when(cx.prepareStatement("bank-approved-test-sql")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        AtomicInteger balanceCalls = new AtomicInteger();
        var assembled = assembly(table -> "bank-approved-test-sql", (conn, table, eve) -> {
            assertSame(cx, conn);
            assertEquals("BKEVE", table);
            assertEquals("000421", eve);
            long bindings = mockingDetails(statement).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().matches(
                            "setString|setNull|setBigDecimal|setDate|setInt|setLong|setDouble|setObject"))
                    .count();

            assertEquals(233, bindings, "Les 233 paramètres BKEVE doivent être liés");

            verify(statement).setString(4, "000421");
            verify(statement).setBigDecimal(16, new BigDecimal("1000"));
            verify(statement).setBigDecimal(37, new BigDecimal("990"));
            verify(statement).setDate(93, java.sql.Date.valueOf(DAY));
            verify(statement).executeUpdate();
            verify(statement).executeUpdate();
            balanceCalls.incrementAndGet();
        });
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (conn, op, table, day) -> "000421");
        coordinator.allocateAndWrite("001", "BKEVE", DAY, (conn, eve) -> {
            assembled.execute(conn, eve, event(), context(eve, "BKEVE"), suppliedBankValues());
            return eve;
        });
        assertEquals(1, balanceCalls.get());
        verify(cx, times(1)).commit();
        verify(cx, never()).rollback();
    }

    @Test
    void missingBankValuesAreBoundAsSqlNulls() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement("bank-test-sql"))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        AtomicInteger balanceCalls = new AtomicInteger();

        var assembled = assembly(
                table -> "bank-test-sql",
                (conn, table, eve) -> balanceCalls.incrementAndGet()
        );

        assertDoesNotThrow(() ->
                assembled.execute(
                        connection,
                        "000421",
                        event(),
                        context("000421", "BKEVE"),
                        Map.of()
                )
        );

        verify(statement).setString(4, "000421");

        verify(statement).setNull(
                eq(BkeveJdbcColumnOrder.COLUMNS.indexOf("DEV1") + 1),
                anyInt()
        );

        verify(statement, times(1)).executeUpdate();
        assertEquals(1, balanceCalls.get());
    }

    @Test void staleNonreservedContextEveIsRefusedBeforeInsert() throws Exception {
        Connection cx = mock(Connection.class);
        var assembled = assembly(table -> "not-used", (c, t, e) -> fail("balances must not run"));
        assertThrows(IllegalStateException.class,
                () -> assembled.execute(cx, "000421", event(), context("000422", "BKEVE"), suppliedBankValues()));
        verify(cx, never()).prepareStatement(anyString());
    }

    @Test void nightBalanceFailureRollsBackWithoutRetryOrCommit() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection cx = transactionMock();
        PreparedStatement statement = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(cx);
        when(cx.prepareStatement("bank-night-test-sql")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        AtomicInteger attempts = new AtomicInteger();
        var assembled = assembly(table -> "BKEVE_EOD".equals(table) ? "bank-night-test-sql" : null,
                (conn, table, eve) -> { attempts.incrementAndGet(); throw new SQLException("test balance failure"); });
        var coordinator = new JdbcBkeveTransactionCoordinator(ds, (conn, op, table, day) -> "000422");
        assertThrows(RuntimeException.class,
                () -> coordinator.allocateAndWrite("001", "BKEVE_EOD", DAY, (conn, eve) -> {
                    assembled.execute(conn, eve, event(), context(eve, "BKEVE_EOD"), suppliedBankValues());
                    return eve;
                }));
        assertEquals(1, attempts.get());
        verify(statement, times(1)).executeUpdate();
        verify(cx, times(1)).rollback();
        verify(cx, never()).commit();
    }
}
