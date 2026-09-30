package com.regional.corebanking.payment.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.payment.application.exception.PaymentExecutionIdempotencyConflictException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Supplier;

public class JdbcPaymentExecutionPersistenceAdapter implements PaymentExecutionPersistencePort {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public JdbcPaymentExecutionPersistenceAdapter(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.json = json;
    }

    @Override
    public PaymentExecutionResult executeIdempotent(
            String institution,
            String key,
            String fingerprint,
            PaymentExecutionCommand command,
            Supplier<PaymentExecutionResult> execution) {

        Boolean claimed = transactions.execute(status -> jdbc.update("""
                INSERT INTO regional_payment_execution
                  (institution_code, payment_reference, idempotency_key,
                   request_fingerprint, operation_code, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (institution_code, idempotency_key) DO NOTHING
                """,
                institution,
                command.paymentReference(),
                key,
                fingerprint,
                command.providerEvent().operationCode()) == 1);

        if (!Boolean.TRUE.equals(claimed)) {
            StoredExecution existing = findStoredByIdempotencyKey(institution, key)
                    .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
            validateFingerprint(existing.requestFingerprint(), fingerprint);
            if (existing.result() != null) {
                return existing.result();
            }
            throw unresolved();
        }

        try {
            PaymentExecutionResult result = execution.get();
            transactions.executeWithoutResult(status -> storeResult(institution, key, result));
            return result;
        } catch (RuntimeException exception) {
            // The technical claim is deliberately preserved. We cannot know whether
            // a generic banking/transport failure happened before or after financial
            // commit, therefore a later call must recover rather than execute again.
            transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE regional_payment_execution
                       SET outcome = 'UNKNOWN'
                     WHERE institution_code = ? AND idempotency_key = ?
                    """, institution, key));
            throw exception;
        }
    }

    @Override
    public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String paymentReference) {
        return jdbc.query("""
                SELECT result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND payment_reference = ?
                """,
                (rs, row) -> deserialize(rs.getString(1)),
                institution, paymentReference).stream().findFirst().flatMap(Optional::ofNullable);
    }

    @Override
    public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
        return jdbc.query("""
                SELECT result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND idempotency_key = ?
                """,
                (rs, row) -> deserialize(rs.getString(1)),
                institution, key).stream().findFirst().flatMap(Optional::ofNullable);
    }

    private Optional<StoredExecution> findStoredByIdempotencyKey(String institution, String key) {
        return jdbc.query("""
                SELECT request_fingerprint, result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND idempotency_key = ?
                """,
                (rs, row) -> new StoredExecution(rs.getString(1), deserialize(rs.getString(2))),
                institution, key).stream().findFirst();
    }

    private void storeResult(String institution, String key, PaymentExecutionResult result) {
        jdbc.update("""
                UPDATE regional_payment_execution
                   SET outcome = ?,
                       bank_reference = ?,
                       reason_code = ?,
                       result_json = CAST(? AS jsonb),
                       completed_at = CURRENT_TIMESTAMP
                 WHERE institution_code = ? AND idempotency_key = ?
                """,
                result.outcome().name(),
                result.bankReference(),
                result.reasonCode(),
                serialize(result),
                institution,
                key);
    }

    private static void validateFingerprint(String existing, String requested) {
        if (!MessageDigest.isEqual(
                existing.getBytes(StandardCharsets.US_ASCII),
                requested.getBytes(StandardCharsets.US_ASCII))) {
            throw new PaymentExecutionIdempotencyConflictException(
                    "Idempotency key is already associated with a different Payment Execution request");
        }
    }

    private String serialize(PaymentExecutionResult result) {
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Payment Execution result", exception);
        }
    }

    private PaymentExecutionResult deserialize(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, PaymentExecutionResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Payment Execution result", exception);
        }
    }

    private static PaymentExecutionUnavailableException unresolved() {
        return new PaymentExecutionUnavailableException(
                "Payment Execution outcome is unresolved; authoritative recovery is required before any replay");
    }

    private record StoredExecution(String requestFingerprint, PaymentExecutionResult result) {
    }
}
