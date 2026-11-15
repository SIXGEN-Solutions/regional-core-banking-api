package com.regional.corebanking.payment.infrastructure.amplitude;

import java.util.List;

/**
 * Exact column order from legacy bkeve_short(2).java getSaveQuery/getQueryValues.
 * The statement is still subject to physical-schema and bank approval.
 * Identifier selection is restricted to the two legacy event tables.
 */
final class BkeveInsertSql {
    private BkeveInsertSql() {}

    static String insertFor(String table) {
        if (!"BKEVE".equals(table) && !"BKEVE_EOD".equals(table))
            throw new IllegalArgumentException("Unsupported BKEVE physical table");
        List<String> columns = BkeveJdbcColumnOrder.COLUMNS;
        if (columns.size() != 233)
            throw new IllegalStateException("BKEVE requires exactly 233 columns");
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" +
                String.join(", ", java.util.Collections.nCopies(233, "?")) + ")";
    }
}
