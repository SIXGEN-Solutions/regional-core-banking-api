package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyBkeveJdbcSqlTest {
    private static LegacyBkeveIndicativeBalanceUpdate.Leg leg(String ncp, String amount) {
        return new LegacyBkeveIndicativeBalanceUpdate.Leg("00001", "001", ncp, "00", "42", new BigDecimal(amount));
    }

    @Test void legacyInsertHasExactly233ColumnsAndParametersAndStrictTableChoice() {
        var sql = new LegacyBkeveSql();
        for (String table : new String[] {"BKEVE", "BKEVE_EOD"}) {
            String insert = sql.insertFor(table);
            assertTrue(insert.startsWith("INSERT INTO " + table + " (AGSA, AGE, OPE, EVE"));
            assertEquals(233, insert.chars().filter(ch -> ch == '?').count());
            assertTrue(insert.contains("REFDOS, TCHFR) VALUES"));
        }
        assertThrows(IllegalArgumentException.class, () -> sql.insertFor("BKEVE;DROP TABLE BKEVE"));
    }

    @Test void normalModeCreditsThenDebitsExactlyOnceWithoutCommit() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        PreparedStatement credit = mock(PreparedStatement.class);
        PreparedStatement debit = mock(PreparedStatement.class);
        when(c.prepareStatement(contains("sin = sin +"))).thenReturn(credit);
        when(c.prepareStatement(contains("sin = sin -"))).thenReturn(debit);
        when(credit.executeUpdate()).thenReturn(1);
        when(debit.executeUpdate()).thenReturn(1);
        new LegacyBkeveIndicativeBalanceUpdate(leg("12345678901", "1000"), leg("12345678902", "990"), true)
                .update(c, "BKEVE", "000123");
        var ordered = inOrder(credit, debit);
        ordered.verify(credit).executeUpdate();
        ordered.verify(debit).executeUpdate();
        verify(c, never()).commit();
        verify(c, never()).rollback();
    }

    @Test void inapplicableOperationWritesNothingAndUnknownClassificationFailsClosed() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        var d = leg("12345678901", "1000"); var cr = leg("12345678902", "990");
        assertThrows(IllegalArgumentException.class,
                () -> new LegacyBkeveIndicativeBalanceUpdate(d, cr, null));
        new LegacyBkeveIndicativeBalanceUpdate(d, cr, false).update(c, "BKEVE", "000124");
        verify(c, never()).prepareStatement(anyString());
    }

    @Test void aFailedBalanceWritePropagatesForTransactionRollback() throws Exception {
        Connection c = mock(Connection.class);
        when(c.getAutoCommit()).thenReturn(false);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(c.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        var updater = new LegacyBkeveIndicativeBalanceUpdate(leg("12345678901", "1000"), leg("12345678902", "990"), true);
        assertThrows(java.sql.SQLException.class, () -> updater.update(c, "BKEVE", "000123"));
        verify(c, never()).commit();
    }
}
