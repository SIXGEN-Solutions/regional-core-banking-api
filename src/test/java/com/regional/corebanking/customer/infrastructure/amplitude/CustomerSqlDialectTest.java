package com.regional.corebanking.customer.infrastructure.amplitude;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerSqlDialectTest {

    @Test
    void niuUsesCurrentBkcliNidColumn() {
        CustomerSqlDialect informix = new InformixCustomerSqlDialect();
        CustomerSqlDialect oracle = new OracleCustomerSqlDialect();

        assertThat(informix.searchByNiu()).contains("bkcli.nid = ?");
        assertThat(oracle.searchByNiu()).contains("bkcli.nid = ?");
        assertThat(informix.searchByNiu()).isEqualTo(oracle.searchByNiu());
    }

    @Test
    void bothVendorsUsePreparedAnsiQueries() {
        CustomerSqlDialect informix = new InformixCustomerSqlDialect();
        CustomerSqlDialect oracle = new OracleCustomerSqlDialect();

        assertThat(informix.searchByCustomerNumber())
                .contains("join bkcli")
                .contains("?")
                .doesNotContain("' +");
        assertThat(oracle.searchByCustomerNumber())
                .isEqualTo(informix.searchByCustomerNumber());
    }

    @Test
    void availableBalanceUsesBankProvidedSqlForBothDialects() {
        CustomerSqlDialect informix = new InformixCustomerSqlDialect();
        CustomerSqlDialect oracle = new OracleCustomerSqlDialect();
        assertThat(informix.checkNightMode()).contains("from bknom").contains("mnt4");
        assertThat(informix.availableBalanceNormal())
                .contains("a.sin-nvl(a.minds,0)+nvl(maut,0)")
                .contains("left join bkautc")
                .contains("eta in ('VA','VF','FO')")
                .contains("a.cfe='N'")
                .contains("a.ife='N'");
        assertThat(informix.availableBalanceNight())
                .contains("trim(a.cfe)='N'")
                .contains("trim(a.ife)='N'");
        assertThat(oracle.availableBalanceNormal()).isEqualTo(informix.availableBalanceNormal());
        assertThat(oracle.availableBalanceNight()).isEqualTo(informix.availableBalanceNight());
    }

    @Test
    void onlyInformixAppliesDirtyReadSessionInstruction() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        new InformixCustomerSqlDialect().prepareConnection(connection);

        verify(statement).execute("SET ISOLATION TO DIRTY READ");
        verify(statement).close();

        clearInvocations(connection, statement);

        new OracleCustomerSqlDialect().prepareConnection(connection);

        verifyNoInteractions(statement);
    }
}
