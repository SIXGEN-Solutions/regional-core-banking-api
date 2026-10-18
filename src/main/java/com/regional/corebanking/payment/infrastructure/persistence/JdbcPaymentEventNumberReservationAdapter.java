package com.regional.corebanking.payment.infrastructure.persistence;

import com.regional.corebanking.payment.application.port.out.PaymentEventNumberReservationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;

/**
 * PostgreSQL technical EVE water mark, adapted from EODProcess(id,lasteve,version).
 * No JPA entity is needed; no participant/partner constants are carried over.
 * A reserved number is NEVER financial confirmation.
 */
public final class JdbcPaymentEventNumberReservationAdapter implements PaymentEventNumberReservationPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcPaymentEventNumberReservationAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
    }

    @Override
    public String reserve(String institution, String key, String operation, String firstCandidate) {
        Objects.requireNonNull(institution);
        Objects.requireNonNull(key);
        Objects.requireNonNull(operation);
        if (operation.isBlank()) throw new IllegalArgumentException("Missing OPE");
        final int bankCandidate = parse(firstCandidate);
        return Objects.requireNonNull(transactions.execute(tx -> {
            // Keep the existing EODProcess table; initializing missing operation
            // rows avoids copying legacy MAC/ORANGE/NANO defaults.
            jdbc.update("INSERT INTO api_eodprocess (id, lasteve, version) VALUES (?, NULL, 0) " +
                    "ON CONFLICT (id) DO NOTHING", operation);
            String last = jdbc.queryForObject(
                    "SELECT lasteve FROM api_eodprocess WHERE id = ? FOR UPDATE",
                    String.class, operation);
            int lastAssigned = last == null || last.isBlank() ? 0 : parse(last);
            int candidate = Math.max(bankCandidate, lastAssigned + 1);
            if (candidate > 999999) {
                throw new IllegalStateException("EVE sequence exhausted for OPE " + operation);
            }
            String eve = "%06d".formatted(candidate);
            int claimed = jdbc.update("UPDATE regional_payment_execution SET event_number = ? " +
                    "WHERE institution_code = ? AND idempotency_key = ? AND operation_code = ? " +
                    "AND event_number IS NULL", candidate, institution, key, operation);
            if (claimed != 1) throw new IllegalStateException("Missing or already allocated payment claim");
            int updated = jdbc.update("UPDATE api_eodprocess SET lasteve = ?, version = version + 1 WHERE id = ?",
                    eve, operation);
            if (updated != 1) throw new IllegalStateException("EODProcess watermark update failed");
            return eve;
        }));
    }

    private static int parse(String eve) {
        if (eve == null || !eve.matches("[0-9]{6}"))
            throw new IllegalArgumentException("Invalid six-digit EVE");
        int n = Integer.parseInt(eve);
        if (n <= 0 || n > 999999) throw new IllegalArgumentException("Invalid EVE range");
        return n;
    }
}
