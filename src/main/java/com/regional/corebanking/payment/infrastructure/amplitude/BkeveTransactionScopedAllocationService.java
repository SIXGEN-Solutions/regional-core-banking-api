package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Preparatory transaction-scoped coordination; not wired to payment execution. */
final class BkeveTransactionScopedAllocationService {
    private static final Set<String> TABLES = Set.of("BKEVE", "BKEVE_EOD");
    private static final Pattern NUMBER = Pattern.compile("[0-9]{6}");
    private final BkeveTransactionScopedAllocationPort port;

    BkeveTransactionScopedAllocationService(BkeveTransactionScopedAllocationPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    <T> T execute(String operationCode, String eventTable, LocalDate accountingDate,
                  BkeveTransactionScopedAllocationPort.FinancialWrite<T> write) throws Exception {
        if (operationCode == null || operationCode.isBlank()) {
            throw new IllegalArgumentException("operationCode is required");
        }
        if (!TABLES.contains(eventTable)) {
            throw new IllegalArgumentException("Unsupported BKEVE event table");
        }
        Objects.requireNonNull(accountingDate, "accountingDate");
        Objects.requireNonNull(write, "write");
        return port.allocateAndWrite(operationCode, eventTable, accountingDate, number -> {
            if (number == null || !NUMBER.matcher(number).matches() || "000000".equals(number)) {
                throw new IllegalStateException("Invalid bank-allocated EVE");
            }
            return write.execute(number);
        });
    }
}
