package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class BkeveCurrentSqlAndBalanceTest {
    private static BkeveIndicativeBalanceStatements.Account account(String ncp) {
        return new BkeveIndicativeBalanceStatements.Account("00001", "001", ncp, "00", "42");
    }
    @Test void insertHas233ColumnsAndOnlyTwoPermittedTables() {
        for (String table : new String[]{"BKEVE", "BKEVE_EOD"}) {
            String sql = BkeveInsertSql.insertFor(table);
            assertTrue(sql.startsWith("INSERT INTO " + table + " (AGSA, AGE, OPE, EVE"));
            assertEquals(233, sql.chars().filter(ch -> ch == '?').count());
            assertTrue(sql.contains("REFDOS, TCHFR) VALUES"));
        }
        assertThrows(IllegalArgumentException.class, () -> BkeveInsertSql.insertFor("BKEVE;DROP TABLE BKEVE"));
    }
    @Test void normalCreditThenDebitWithoutCommitting() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        PreparedStatement credit = mock(PreparedStatement.class);
        PreparedStatement debit = mock(PreparedStatement.class);
        when(c.prepareStatement(contains("sin = sin +"))).thenReturn(credit);
        when(c.prepareStatement(contains("sin = sin -"))).thenReturn(debit);
        when(credit.executeUpdate()).thenReturn(1);
        when(debit.executeUpdate()).thenReturn(1);
        new BkeveIndicativeBalanceStatements().execute(c, BkeveIndicativeBalanceStatements.Mode.NORMAL,
            account("12345678901"), account("12345678902"),
            new BigDecimal("1000"), new BigDecimal("990"), true);
        var order = inOrder(credit, debit);
        order.verify(credit).executeUpdate();
        order.verify(debit).executeUpdate();
        verify(c, never()).commit();
        verify(c, never()).rollback();
    }
    @Test void rejectedPolicyDoesNotPrepareAnyStatement() throws Exception {
        Connection c = mock(Connection.class);
        assertThrows(IllegalStateException.class, () ->
            new BkeveIndicativeBalanceStatements().execute(c, BkeveIndicativeBalanceStatements.Mode.NORMAL,
                account("12345678901"), account("12345678902"), BigDecimal.ONE, BigDecimal.ONE, false));
        verify(c, never()).prepareStatement(anyString());
    }
    @Test void failedBalanceUpdatePropagatesWithoutCommit() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(c.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        assertThrows(SQLException.class, () ->
            new BkeveIndicativeBalanceStatements().execute(c, BkeveIndicativeBalanceStatements.Mode.NORMAL,
                account("12345678901"), account("12345678902"), BigDecimal.ONE, BigDecimal.ONE, true));
        verify(c, never()).commit();
    }
    @Test void nightWritesSignedMovementsBeforeSyntheticBalances() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        PreparedStatement night = mock(PreparedStatement.class);
        PreparedStatement credit = mock(PreparedStatement.class);
        PreparedStatement debit = mock(PreparedStatement.class);
        when(c.prepareStatement(contains("insert into bksin"))).thenReturn(night);
        when(c.prepareStatement(contains("update syn_bkcom set sin = sin +"))).thenReturn(credit);
        when(c.prepareStatement(contains("update syn_bkcom set sin = sin -"))).thenReturn(debit);
        when(night.executeUpdate()).thenReturn(1);
        when(credit.executeUpdate()).thenReturn(1);
        when(debit.executeUpdate()).thenReturn(1);
        new BkeveIndicativeBalanceStatements().execute(c, BkeveIndicativeBalanceStatements.Mode.NIGHT,
            account("12345678901"), account("12345678902"),
            new BigDecimal("1000"), new BigDecimal("990"), true);
        var order = inOrder(night, credit, debit);
        order.verify(night, times(2)).executeUpdate();
        order.verify(credit).executeUpdate();
        order.verify(debit).executeUpdate();
        verify(night).setBigDecimal(5, new BigDecimal("-1000"));
        verify(night).setBigDecimal(5, new BigDecimal("990"));
        verify(c, never()).commit();
    }
}
