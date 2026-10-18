package com.regional.corebanking.payment.infrastructure.amplitude;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * R6.6.7: bank-owned indicative balance update on the BKEVE transaction connection.
 * No SQL, target table, banking operation rules or financial calculations are assumed.
 * Implement only after La Regionale approves the physical mutation and semantics.
 */
@FunctionalInterface
interface BkeveIndicativeBalanceUpdate {
    /**
     * Called after BKEVE INSERT and before transaction commit, on the same JDBC
     * connection. Failure MUST propagate so the transaction coordinator rolls back.
     * Implementations MUST NOT commit, rollback, close or retry this connection.
     */
    void update(Connection connection, String eventTable, String eventNumber) throws SQLException;

    /** Check configuration before the first bank write. Implementations may
     * check operation/nature eligibility here without mutating the connection. */
    default void validateBeforeWrite(String eventTable, String eventNumber) {
    }

    static BkeveIndicativeBalanceUpdate unconfigured() {
        return new BkeveIndicativeBalanceUpdate() {
            @Override
            public void validateBeforeWrite(String eventTable, String eventNumber) {
                throw new IllegalStateException("Bank-approved indicative balance update is not configured");
            }
            @Override
            public void update(Connection connection, String eventTable, String eventNumber) {
                throw new IllegalStateException("Bank-approved indicative balance update is not configured");
            }
        };
    }
}
