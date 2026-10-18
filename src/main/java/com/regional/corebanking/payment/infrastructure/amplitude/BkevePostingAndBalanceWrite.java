package com.regional.corebanking.payment.infrastructure.amplitude;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Pre-activation composition of BKEVE insert and indicative balance mutation.
 * MUST be called only inside JdbcBkeveTransactionCoordinator.FinancialWrite.
 * Does not obtain a JDBC connection and never commits, rolls back or retries.
 */
final class BkevePostingAndBalanceWrite {
    private final BkeveJdbcPostingAdapter posting;
    private final BkeveIndicativeBalanceUpdate balances;

    BkevePostingAndBalanceWrite(BkeveJdbcPostingAdapter posting,
                                BkeveIndicativeBalanceUpdate balances) {
        this.posting = Objects.requireNonNull(posting, "posting");
        this.balances = Objects.requireNonNull(balances, "balances");
    }

    void execute(Connection connection, String eventTable, String eventNumber,
                 BkeveJdbcParameterMapper.Parameters parameters) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(parameters, "parameters");
        if (eventNumber == null || !eventNumber.matches("[0-9]{6}") || "000000".equals(eventNumber)) {
            throw new IllegalArgumentException("Invalid bank-allocated event number");
        }
        // Refuse unknown balance rules BEFORE creating a bank side effect when possible:
        // actual configuration is an explicit dependency, not inferred from legacy SQL.
        balances.validateBeforeWrite(eventTable, eventNumber);
        posting.insert(connection, eventTable, parameters);
        balances.update(connection, eventTable, eventNumber);
    }
}
