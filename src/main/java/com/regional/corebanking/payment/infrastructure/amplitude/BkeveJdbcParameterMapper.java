package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * R6.6.3: complete positional mapping, not an executable Amplitude posting adapter.
 * Values not supported by the approved client contract must come from a separate
 * bank-authoritative resolver and MUST be explicitly present, including SQL NULLs.
 * Legacy defaults are intentionally NOT inferred or applied.
 */
public final class BkeveJdbcParameterMapper {
    private static final Set<String> CLIENT_COLUMNS = Set.of(
            "AGSA", "AGE", "OPE", "MHT1", "MON1", "MHT2", "MON2", "LIB1", "CSP1");
    private static final Set<String> ALL_COLUMNS = Set.copyOf(BkeveJdbcColumnOrder.COLUMNS);

    public record Parameters(List<String> columns, List<Object> values) {
        public Parameters {
            Objects.requireNonNull(columns, "columns");
            Objects.requireNonNull(values, "values");
            if (columns.size() != 233 || values.size() != 233) {
                throw new IllegalArgumentException("Exactly 233 BKEVE columns and values required");
            }
            if (!columns.equals(BkeveJdbcColumnOrder.COLUMNS)) {
                throw new IllegalArgumentException("BKEVE column ordering mismatch");
            }
            columns = List.copyOf(columns);
            values = Collections.unmodifiableList(new ArrayList<>(values)); // permits explicit SQL NULL
        }

        public Object valueAt(int oneBasedPosition) {
            if (oneBasedPosition < 1 || oneBasedPosition > 233) {
                throw new IndexOutOfBoundsException("BKEVE position: " + oneBasedPosition);
            }
            return values.get(oneBasedPosition - 1);
        }
    }

    public Parameters map(PaymentProviderEvent event, Map<String, ?> approvedBankValues) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(approvedBankValues, "approvedBankValues");
        requireText(event.operationCode(), "operationCode");
        requireText(event.agsa(), "agsa");
        requireText(event.age(), "age");
        requireText(event.csp1(), "csp1");
        requireText(event.label(), "label");
        requirePositive(event.debitAmount(), "debitAmount");
        requirePositive(event.creditAmount(), "creditAmount");

        if (!ALL_COLUMNS.containsAll(approvedBankValues.keySet())) {
            throw new IllegalArgumentException("Unknown BKEVE bank-resolved columns");
        }
        for (String key : CLIENT_COLUMNS) {
            if (approvedBankValues.containsKey(key)) {
                throw new IllegalArgumentException("Bank resolver must not override caller column: " + key);
            }
        }
        Map<String, Object> values = new HashMap<>(approvedBankValues);
        // Approved R6.6.2 semantic equivalences. No payment-domain amount is used.
        values.put("AGSA", event.agsa());
        values.put("AGE", event.age());
        values.put("OPE", event.operationCode());
        values.put("MHT1", event.debitAmount());
        values.put("MON1", event.debitAmount());
        values.put("MHT2", event.creditAmount());
        values.put("MON2", event.creditAmount());
        values.put("LIB1", event.label());
        values.put("CSP1", event.csp1());

        Set<String> missing = new HashSet<>(ALL_COLUMNS);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            // No fallback to uncertain legacy default values.
            throw new IllegalStateException("Unresolved BKEVE columns (bank evidence required): "
                    + missing.stream().sorted().toList());
        }
        List<Object> ordered = new ArrayList<>(233);
        for (String column : BkeveJdbcColumnOrder.COLUMNS) {
            ordered.add(values.get(column));
        }
        return new Parameters(BkeveJdbcColumnOrder.COLUMNS, ordered);
    }

    /** Parameter binding only. Does not prepare, execute, or commit an INSERT. */
    public void bind(PreparedStatement statement, Parameters parameters) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(parameters, "parameters");
        for (int pos = 1; pos <= 233; pos++) {
            statement.setObject(pos, parameters.valueAt(pos));
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
