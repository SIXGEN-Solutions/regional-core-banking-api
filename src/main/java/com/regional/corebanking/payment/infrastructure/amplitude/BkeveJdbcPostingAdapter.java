package com.regional.corebanking.payment.infrastructure.amplitude;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * R6.6.6 preparatory, transaction-participating BKEVE JDBC writer.
 * Never creates SQL from unapproved historical schema evidence.
 * Never opens its own connection, commits, rolls back or retries.
 * Unwired: production posting remains fail-closed.
 */
final class BkeveJdbcPostingAdapter {
    @FunctionalInterface
    interface BankApprovedInsertStatements {
        /**
         * Bank-verified SQL for exactly the chosen physical table and the 233
         * explicitly approved positional values. The implementation MUST be
         * reviewed for identifier/placeholder/type compatibility; no default.
         */
        String insertFor(String table);
    }

    private final BankApprovedInsertStatements statements;
    private final BkeveJdbcParameterMapper mapper;

    BkeveJdbcPostingAdapter(BankApprovedInsertStatements statements,
                            BkeveJdbcParameterMapper mapper) {
        this.statements = Objects.requireNonNull(statements, "statements");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Caller must supply the SAME transaction-scoped connection used for EVE
     * allocation; caller is responsible for commit and authoritative recovery.
     */
    void insert(Connection connection, String eventTable,
                BkeveJdbcParameterMapper.Parameters parameters) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(parameters, "parameters");
        if (!"BKEVE".equals(eventTable) && !"BKEVE_EOD".equals(eventTable)) {
            throw new IllegalArgumentException("Unsupported BKEVE event table");
        }
        if (connection.getAutoCommit()) {
            throw new IllegalStateException("BKEVE INSERT requires an existing caller-owned JDBC transaction");
        }
        String approvedSql = statements.insertFor(eventTable);
        if (approvedSql == null || approvedSql.isBlank()) {
            throw new IllegalStateException("No approved physical INSERT SQL for " + eventTable);
        }
        // SQL is a bank-approved injected dependency, not generated from legacy CSV.
        try (PreparedStatement statement = connection.prepareStatement(approvedSql)) {
            mapper.bind(statement, parameters);
            int affected = statement.executeUpdate();
            if (affected != 1) {
                throw new SQLException("BKEVE INSERT affected unexpected row count: " + affected);
            }
        }
    }
}
