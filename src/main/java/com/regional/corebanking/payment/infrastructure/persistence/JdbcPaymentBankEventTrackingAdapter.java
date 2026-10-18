package com.regional.corebanking.payment.infrastructure.persistence;

import com.regional.corebanking.payment.application.port.out.PaymentBankEventTrackingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL allocation lookup, not an execution outcome lookup. */
public final class JdbcPaymentBankEventTrackingAdapter implements PaymentBankEventTrackingPort {
    private final JdbcTemplate jdbc;
    public JdbcPaymentBankEventTrackingAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }
    @Override
    public Optional<TrackedEvent> findByPaymentReference(String institution, String reference) {
        return query("payment_reference", institution, reference);
    }
    @Override
    public Optional<TrackedEvent> findByIdempotencyKey(String institution, String key) {
        return query("idempotency_key", institution, key);
    }
    private Optional<TrackedEvent> query(String selector, String institution, String value) {
        // selector is restricted to exactly two hard-coded constants above.
        String sql = "SELECT payment_reference, operation_code, event_number FROM regional_payment_execution "
                + "WHERE institution_code = ? AND " + selector + " = ?";
        return jdbc.query(sql, (rs, row) -> {
            long eve = rs.getLong("event_number");
            if (rs.wasNull()) return null;
            if (eve <= 0 || eve > 999999)
                throw new IllegalStateException("Invalid PostgreSQL event tracking number");
            return new TrackedEvent(rs.getString("payment_reference"),
                    rs.getString("operation_code"), "%06d".formatted(eve));
        }, institution, value).stream().filter(Objects::nonNull).findFirst();
    }
}
