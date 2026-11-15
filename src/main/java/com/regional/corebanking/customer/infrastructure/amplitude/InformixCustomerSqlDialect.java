package com.regional.corebanking.customer.infrastructure.amplitude;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class InformixCustomerSqlDialect extends AbstractAnsiCustomerSqlDialect {

    @Override
    public void prepareConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET ISOLATION TO DIRTY READ");
        }
    }
}
