package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bank event enrichment from the authoritative banking context.
 * It does NOT allocate EVE, query accounts, derive currencies or approve INSERT.
 * Remaining BKEVE columns must come from separate validated banking evidence.
 */
final class BkeveBankEnrichmentService {
    private static final Set<String> CONTEXT_COLUMNS = Set.of("EVE", "DCO", "DVA1", "DVA2");
    private static final Set<String> CLIENT_COLUMNS = Set.of(
            "AGSA", "AGE", "OPE", "MHT1", "MON1", "MHT2", "MON2", "LIB1", "CSP1");
    private static final Set<String> ALL_COLUMNS = Set.copyOf(BkeveJdbcColumnOrder.COLUMNS);

    /**
     * Returns an immutable mapping with four authoritative context positions.
     * Date values remain LocalDate; approved JDBC type/format is a later decision.
     * An explicit bank value for any other column, including null, is retained.
     */
    Map<String, Object> enrich(PaymentExecutionBankingContext context,
                               Map<String, ?> validatedBankValues) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(validatedBankValues, "validatedBankValues");
        requireText(context.eventNumber(), "eventNumber");
        requireDate(context.accountingDate(), "accountingDate");
        requireDate(context.debitValueDate(), "debitValueDate");
        requireDate(context.creditValueDate(), "creditValueDate");
        // The banking context resolver supplies these values. Currency is
        // deliberately not mapped to DEV/DEV1/DEV2 without validated code conversion.
        for (String column : validatedBankValues.keySet()) {
            if (!ALL_COLUMNS.contains(column)) {
                throw new IllegalArgumentException("Unknown BKEVE column: " + column);
            }
            if (CONTEXT_COLUMNS.contains(column)) {
                throw new IllegalArgumentException("Authoritative banking context cannot be overridden: " + column);
            }
            if (CLIENT_COLUMNS.contains(column)) {
                throw new IllegalArgumentException("Caller-owned BKEVE column cannot be overridden: " + column);
            }
        }
        Map<String, Object> result = new HashMap<>(validatedBankValues);
        result.put("EVE", context.eventNumber());
        result.put("DCO", context.accountingDate());
        result.put("DVA1", context.debitValueDate());
        result.put("DVA2", context.creditValueDate());
        return Collections.unmodifiableMap(result);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing authoritative banking context: " + name);
        }
    }

    private static void requireDate(LocalDate value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Missing authoritative banking context: " + name);
        }
    }
}
