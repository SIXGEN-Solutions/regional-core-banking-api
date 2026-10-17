package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Banking JDBC transaction scope shared conceptually by ALL API instances.
 * No JVM-local locks, MAX(eve), or SQL are prescribed here.
 * A bank-approved allocator MUST acquire database-level exclusion on the provided
 * connection and keep it until the BKEVE write and commit are complete.
 * Never connect this coordinator to production until bank evidence is approved.
 */
final class JdbcBkeveTransactionCoordinator {
    // TEMPORARY SINGLE-JVM MODE ONLY: shared by all coordinator objects in this process.
    // This does NOT protect against other application processes or Amplitude producers.
    private static final ReentrantLock SINGLE_INSTANCE_LOCK = new ReentrantLock(true);
    @FunctionalInterface
    interface LockedAllocator {
        String allocate(Connection connection, String operationCode, String eventTable,
                        LocalDate accountingDate) throws Exception;
    }

    @FunctionalInterface
    interface FinancialWrite<T> {
        T execute(Connection connection, String eventNumber) throws Exception;
    }

    private final DataSource dataSource;
    private final LockedAllocator allocator;

    JdbcBkeveTransactionCoordinator(DataSource dataSource, LockedAllocator allocator) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    <T> T allocateAndWrite(String operationCode, String eventTable, LocalDate accountingDate,
                           FinancialWrite<T> write) {
        if (operationCode == null || operationCode.isBlank())
            throw new IllegalArgumentException("operationCode is required");
        if (!"BKEVE".equals(eventTable) && !"BKEVE_EOD".equals(eventTable))
            throw new IllegalArgumentException("Unsupported BKEVE event table");
        Objects.requireNonNull(accountingDate, "accountingDate");
        Objects.requireNonNull(write, "write");
        // Serialize the entire allocation + write + commit/rollback scope in this JVM.
        // All production callers MUST use this coordinator; no direct MAX(eve) posting.
        SINGLE_INSTANCE_LOCK.lock();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit())
                throw new PaymentExecutionUnavailableException("BKEVE coordinator requires an independently owned JDBC transaction");
            connection.setAutoCommit(false);
            boolean commitAttempted = false;
            try {
                String eve = allocator.allocate(connection, operationCode, eventTable, accountingDate);
                if (eve == null || !eve.matches("[0-9]{6}") || "000000".equals(eve))
                    throw new IllegalStateException("Invalid bank-allocated EVE");
                T result = write.execute(connection, eve);
                commitAttempted = true;
                connection.commit();
                return result;
            } catch (Exception failure) {
                if (!commitAttempted) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                // Once commit has been attempted, outcome may be UNKNOWN.
                // No automatic callback retry, including on connection failures.
                PaymentExecutionUnavailableException unavailable = new PaymentExecutionUnavailableException(
                    commitAttempted
                      ? "BKEVE commit outcome uncertain; authoritative recovery required"
                      : "BKEVE allocation/write failed; no automatic replay");
                unavailable.initCause(failure);
                throw unavailable;
            }
        } catch (SQLException failure) {
            PaymentExecutionUnavailableException unavailable = new PaymentExecutionUnavailableException(
                "BKEVE JDBC transaction could not be opened or closed; authoritative recovery may be required");
            unavailable.initCause(failure);
            throw unavailable;
        } finally {
            SINGLE_INSTANCE_LOCK.unlock();
        }
    }
}
