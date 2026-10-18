package com.regional.corebanking.payment.infrastructure.amplitude;

import java.util.stream.Collectors;

/** SQL transcribed from the supplied legacy bkeve.java#getSaveQuery, not inferred bank schema. */
final class LegacyBkeveSql implements BkeveJdbcPostingAdapter.BankApprovedInsertStatements {
    @Override public String insertFor(String table) {
        if (!"BKEVE".equals(table) && !"BKEVE_EOD".equals(table))
            throw new IllegalArgumentException("Unsupported BKEVE table");
        // The 233 ordered column names are the extracted legacy INSERT order.
        return "INSERT INTO " + table + " (" + String.join(", ", BkeveJdbcColumnOrder.COLUMNS) +
                ") VALUES (" + BkeveJdbcColumnOrder.COLUMNS.stream()
                .map(ignored -> "?").collect(Collectors.joining(", ")) + ")";
    }
}
