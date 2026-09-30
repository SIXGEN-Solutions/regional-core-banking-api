package com.regional.corebanking.payment.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regional.corebanking.payment.application.exception.PaymentExecutionIdempotencyConflictException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcPaymentExecutionPersistenceAdapterTest {

    private JdbcTemplate jdbc;
    private JdbcPaymentExecutionPersistenceAdapter repository;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("REGIONAL_CONFIRMATION_IT_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "PostgreSQL integration DB not configured");
        assertDedicatedIntegrationDatabase(url);

        DriverManagerDataSource ds = new DriverManagerDataSource(
                url,
                System.getenv("REGIONAL_CONFIRMATION_IT_DB_USERNAME"),
                System.getenv("REGIONAL_CONFIRMATION_IT_DB_PASSWORD"));
        jdbc = new JdbcTemplate(ds);

        String ddl = Files.readString(Path.of("src/main/resources/db/migration/V2__create_payment_execution_store.sql"));
        for (String statement : ddl.split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("DELETE FROM regional_payment_execution");

        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        repository = new JdbcPaymentExecutionPersistenceAdapter(
                jdbc, new DataSourceTransactionManager(ds), json);
    }

    @Test
    void sameKeyAndSameRequestReturnsStoredResultWithoutReexecution() {
        AtomicInteger executions = new AtomicInteger();

        PaymentExecutionResult first = repository.executeIdempotent(
                "REGIONAL", "idem-1", "a".repeat(64), command(),
                () -> { executions.incrementAndGet(); return completed(); });

        PaymentExecutionResult replay = repository.executeIdempotent(
                "REGIONAL", "idem-1", "a".repeat(64), command(),
                () -> { executions.incrementAndGet(); return completed(); });

        assertEquals(first, replay);
        assertEquals(1, executions.get());
    }

    @Test
    void sameKeyAndDifferentRequestFingerprintConflicts() {
        repository.executeIdempotent(
                "REGIONAL", "idem-2", "b".repeat(64), command(), JdbcPaymentExecutionPersistenceAdapterTest::completed);

        assertThrows(PaymentExecutionIdempotencyConflictException.class,
                () -> repository.executeIdempotent(
                        "REGIONAL", "idem-2", "c".repeat(64), command(),
                        JdbcPaymentExecutionPersistenceAdapterTest::completed));
    }

    @Test
    void lookupWorksByPaymentReferenceAndIdempotencyKey() {
        repository.executeIdempotent(
                "REGIONAL", "idem-3", "d".repeat(64), command(), JdbcPaymentExecutionPersistenceAdapterTest::completed);

        assertEquals("BANK-1", repository.findByPaymentReference("REGIONAL", "PAY-1").orElseThrow().bankReference());
        assertEquals("BANK-1", repository.findByIdempotencyKey("REGIONAL", "idem-3").orElseThrow().bankReference());
    }

    @Test
    void unresolvedExecutionCannotBeBlindlyReplayed() {
        assertThrows(IllegalStateException.class,
                () -> repository.executeIdempotent(
                        "REGIONAL", "idem-4", "e".repeat(64), command(),
                        () -> { throw new IllegalStateException("simulated uncertain transport failure"); }));

        assertThrows(PaymentExecutionUnavailableException.class,
                () -> repository.executeIdempotent(
                        "REGIONAL", "idem-4", "e".repeat(64), command(),
                        JdbcPaymentExecutionPersistenceAdapterTest::completed));
    }

    @Test
    void schemaStoresRequiredCorrelationColumnsAndLeavesBankContextNullableUntilKnown() {
        repository.executeIdempotent(
                "REGIONAL", "idem-5", "f".repeat(64), command(), JdbcPaymentExecutionPersistenceAdapterTest::completed);

        var row = jdbc.queryForMap("""
                SELECT institution_code, payment_reference, idempotency_key, request_fingerprint,
                       operation_code, event_number, accounting_date, outcome, bank_reference, reason_code
                  FROM regional_payment_execution
                 WHERE institution_code='REGIONAL' AND idempotency_key='idem-5'
                """);

        assertEquals("REGIONAL", row.get("institution_code"));
        assertEquals("PAY-1", row.get("payment_reference"));
        assertEquals("001", row.get("operation_code"));
        assertNull(row.get("event_number"));
        assertNull(row.get("accounting_date"));
        assertEquals("COMPLETED", row.get("outcome"));
        assertEquals("BANK-1", row.get("bank_reference"));
    }

    private static PaymentExecutionCommand command() {
        return new PaymentExecutionCommand(
                "PAY-1", "v1",
                new PaymentProviderEvent(
                        "PAY-1", "001", "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        new BigDecimal("1000"), "Payment"),
                OffsetDateTime.parse("2026-09-28T20:00:00-04:00"));
    }

    private static PaymentExecutionResult completed() {
        return new PaymentExecutionResult(
                "PAY-1",
                PaymentExecutionOutcome.COMPLETED,
                List.of(
                        new PaymentExecutionCheck(PaymentExecutionCheckType.ACCOUNT_VALID, PaymentExecutionCheckResult.PASS, null),
                        new PaymentExecutionCheck(PaymentExecutionCheckType.DEBIT_ALLOWED, PaymentExecutionCheckResult.PASS, null),
                        new PaymentExecutionCheck(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT, PaymentExecutionCheckResult.PASS, null)),
                "BANK-1",
                null,
                OffsetDateTime.parse("2026-09-28T20:00:01-04:00"));
    }

    private static void assertDedicatedIntegrationDatabase(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        if (!normalized.matches("jdbc:postgresql://[^/]+/regional_core_banking_test(?:\\?.*)?")) {
            throw new IllegalStateException(
                    "Refusing destructive Payment Execution persistence tests against a non-test database: " + url);
        }
    }
}
