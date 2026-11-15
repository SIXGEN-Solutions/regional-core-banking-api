package com.regional.corebanking.payment.infrastructure.amplitude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regional.corebanking.payment.domain.*;
import com.regional.corebanking.payment.infrastructure.persistence.JdbcPaymentBankEventTrackingAdapter;
import com.regional.corebanking.payment.infrastructure.persistence.JdbcPaymentEventNumberReservationAdapter;
import com.regional.corebanking.payment.infrastructure.persistence.JdbcPaymentExecutionPersistenceAdapter;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Actual PostgreSQL technical persistence + mock Amplitude JDBC.
 * This test is never a test of Informix/Oracle physical SQL or bank authorization.
 * Run ONLY against a disposable database named regional_core_banking_test.
 */
class SimulatedPaymentPostgresIT {
    private static final String FIC = "REGIONAL";
    private static final String OPE = "582";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);
    private JdbcTemplate pg;
    private JdbcPaymentExecutionPersistenceAdapter persistence;
    private JdbcPaymentEventNumberReservationAdapter reservation;
    private JdbcPaymentBankEventTrackingAdapter tracking;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("REGIONAL_CONFIRMATION_IT_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Opt-in PostgreSQL IT only");
        if (!url.toLowerCase(Locale.ROOT)
                .matches("jdbc:postgresql://[^/]+/regional_core_banking_test(?:\\?.*)?")) {
            throw new IllegalStateException("Refusing any DB other than regional_core_banking_test");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(url,
                System.getenv("REGIONAL_CONFIRMATION_IT_DB_USERNAME"),
                System.getenv("REGIONAL_CONFIRMATION_IT_DB_PASSWORD"));
        pg = new JdbcTemplate(ds);
        // Existing R6 technical migration is replayed only in the disposable test DB.
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/V2__create_payment_execution_store.sql"));
        pg.execute("DROP TABLE IF EXISTS regional_payment_execution");
        for (String sql : ddl.split(";")) if (!sql.isBlank()) pg.execute(sql);
        // Minimal test fixture of user-provided EODProcess attributes, NOT a production migration.
        pg.execute("DROP TABLE IF EXISTS api_eodprocess");
        pg.execute("""
                CREATE TABLE api_eodprocess (
                  id varchar(64) PRIMARY KEY,
                  lasteve varchar(6),
                  version bigint NOT NULL DEFAULT 0
                )
                """);
        pg.update("DELETE FROM regional_payment_execution WHERE institution_code = ?", FIC);
        pg.update("DELETE FROM api_eodprocess WHERE id = ?", OPE);
        var tx = new DataSourceTransactionManager(ds);
        persistence = new JdbcPaymentExecutionPersistenceAdapter(pg, tx,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        reservation = new JdbcPaymentEventNumberReservationAdapter(pg, tx);
        tracking = new JdbcPaymentBankEventTrackingAdapter(pg);
    }

    private static PaymentExecutionCommand command(String ref) {
        return new PaymentExecutionCommand(ref, "v1",
                new PaymentProviderEvent(ref, OPE, "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        new BigDecimal("1000"), new BigDecimal("990"),
                        "R6610 test", "00001", "00001", "REGIONAL-TEST"),
                OffsetDateTime.parse("2026-10-18T00:00:00Z"));
    }

    private static PaymentExecutionBankingContext context(String eve) {
        return new PaymentExecutionBankingContext(eve, DAY, false, "BKEVE", DAY, DAY, "XAF");
    }

    private record BankJdbc(DataSource dataSource, Connection connection,
                            PreparedStatement insert, PreparedStatement credit,
                            PreparedStatement debit, AtomicInteger insertCount) { }

    private static BankJdbc bank(boolean failDebit) throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        PreparedStatement credit = mock(PreparedStatement.class);
        PreparedStatement debit = mock(PreparedStatement.class);
        AtomicInteger inserts = new AtomicInteger();
        when(ds.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, false, false, false, false);
        when(connection.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert into bkeve")) return insert;
            if (sql.startsWith("update bkcom set sin = sin +")) return credit;
            if (sql.startsWith("update bkcom set sin = sin -")) return debit;
            throw new SQLException("Unrecognized bank SQL in simulation: " + sql);
        });
        when(insert.executeUpdate()).thenAnswer(inv -> { inserts.incrementAndGet(); return 1; });
        when(credit.executeUpdate()).thenReturn(1);
        when(debit.executeUpdate()).thenReturn(failDebit ? 0 : 1);
        return new BankJdbc(ds, connection, insert, credit, debit, inserts);
    }

    private PaymentExecutionResult simulatedFinancialWrite(String key, PaymentExecutionCommand request,
                                                            BankJdbc bank) {
        var mapper = new BkeveJdbcParameterMapper();
        var event = request.providerEvent();
        var coordinator = new JdbcBkeveTransactionCoordinator(bank.dataSource(),
                (conn, ope, table, date) -> reservation.reserve(FIC, key, ope, "000001"));
        String eve = coordinator.allocateAndWrite(OPE, "BKEVE", DAY, (conn, number) -> {
            // Use the reserved event number, not a client-supplied number.
            var ctx = context(number);
            var actualWriter = new BkevePostingAndBalanceWrite(
                    new BkeveJdbcPostingAdapter(mapper),
                    new BkeveExecutionBalanceUpdate(event, ctx));
            var actualAssembly = new BkeveTransactionWriteAssembly(
                    new BkeveBankEnrichmentService(), mapper, actualWriter);
            actualAssembly.execute(conn, number, event, ctx, Map.of());
            return number;
        });
        return new PaymentExecutionResult(request.paymentReference(),
                PaymentExecutionOutcome.COMPLETED, List.of(), OPE + "/" + eve,
                null, OffsetDateTime.now());
    }

    @Test
    void completeSimulatedPaymentPersistsTrackingAndDoesNotReplay() throws Exception {
        var bank = bank(false);
        var cmd = command("PAY-R6610-SUCCESS");
        AtomicInteger executions = new AtomicInteger();
        var first = persistence.executeIdempotent(FIC, "R6610-OK", "a".repeat(64), cmd,
                () -> { executions.incrementAndGet(); return simulatedFinancialWrite("R6610-OK", cmd, bank); });
        assertEquals(PaymentExecutionOutcome.COMPLETED, first.outcome());
        assertEquals("582/000001", first.bankReference());
        assertEquals(1, bank.insertCount().get());
        verify(bank.connection()).commit();
        verify(bank.connection(), never()).rollback();
        verify(bank.credit()).executeUpdate();
        verify(bank.debit()).executeUpdate();
        assertEquals(233, org.mockito.Mockito.mockingDetails(bank.insert()).getInvocations().stream()
                .filter(i -> i.getMethod().getName().matches("setString|setNull|setBigDecimal|setDate|setInt|setLong|setDouble|setObject"))
                .count());
        assertEquals(1L, pg.queryForObject("""
                SELECT event_number FROM regional_payment_execution
                WHERE institution_code = ? AND idempotency_key = ?
                """, Long.class, FIC, "R6610-OK"));
        assertEquals("000001", pg.queryForObject("SELECT lasteve FROM api_eodprocess WHERE id = ?", String.class, OPE));
        assertEquals("000001", tracking.findByIdempotencyKey(FIC, "R6610-OK").orElseThrow().eventNumber());
        assertEquals("000001", tracking.findByPaymentReference(FIC, cmd.paymentReference()).orElseThrow().eventNumber());
        var second = persistence.executeIdempotent(FIC, "R6610-OK", "a".repeat(64), cmd,
                () -> { executions.incrementAndGet(); throw new AssertionError("Financial replay"); });
        assertEquals(first, second);
        assertEquals(1, executions.get());
    }

    @Test
    void recoveryReadsRealPgTrackingAndSimulatedAmplitudeCount() throws Exception {
        // Durable claim/association is written through the REAL PostgreSQL adapters.
        var cmd = command("PAY-R6610-RECOVERY");
        assertThrows(RuntimeException.class, () -> persistence.executeIdempotent(
                FIC, "R6610-REC", "c".repeat(64), cmd, () -> {
                    reservation.reserve(FIC, "R6610-REC", OPE, "000001");
                    throw new IllegalStateException("Simulated response lost after bank commit");
                }));
        assertEquals(PaymentExecutionOutcome.UNKNOWN,
                persistence.findByIdempotencyKey(FIC, "R6610-REC").orElseThrow().outcome());

        // The bank JDBC mock returns one authoritative event in BKEVE, zero in BKEVE_EOD.
        DataSource bank = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement normal = mock(PreparedStatement.class);
        PreparedStatement night = mock(PreparedStatement.class);
        ResultSet normalRows = mock(ResultSet.class);
        ResultSet nightRows = mock(ResultSet.class);
        when(bank.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(contains("FROM BKEVE WHERE"))).thenReturn(normal);
        when(conn.prepareStatement(contains("FROM BKEVE_EOD WHERE"))).thenReturn(night);
        when(normal.executeQuery()).thenReturn(normalRows);
        when(night.executeQuery()).thenReturn(nightRows);
        when(normalRows.next()).thenReturn(true);
        when(nightRows.next()).thenReturn(true);
        when(normalRows.getLong("nbr")).thenReturn(1L);
        when(nightRows.getLong("nbr")).thenReturn(0L);
        var adapter = new JdbcPaymentExecutionBankingAdapter(bank,
                new OraclePaymentExecutionSqlDialect(), FIC,
                BkeveIndicativeBalanceUpdate.unconfigured(), null, tracking);
        var recovered = adapter.findByPaymentReference(FIC, cmd.paymentReference()).orElseThrow();
        assertEquals(PaymentExecutionOutcome.COMPLETED, recovered.outcome());
        assertEquals("582/000001", recovered.bankReference());
        assertEquals(cmd.paymentReference(), recovered.paymentReference());
        assertEquals(PaymentExecutionOutcome.COMPLETED,
                adapter.findByIdempotencyKey(FIC, "R6610-REC").orElseThrow().outcome());
        verify(conn, times(2)).close();
    }

    @Test
    void failedBalanceWriteRollsBackBankButKeepsPgReservationAndUnknown() throws Exception {
        var bank = bank(true);
        var cmd = command("PAY-R6610-ROLLBACK");
        assertThrows(RuntimeException.class, () -> persistence.executeIdempotent(
                FIC, "R6610-FAIL", "b".repeat(64), cmd,
                () -> simulatedFinancialWrite("R6610-FAIL", cmd, bank)));
        verify(bank.connection()).rollback();
        verify(bank.connection(), never()).commit();
        assertEquals(1, bank.insertCount().get());
        assertEquals("000001", pg.queryForObject("SELECT lasteve FROM api_eodprocess WHERE id = ?", String.class, OPE));
        assertEquals("UNKNOWN", pg.queryForObject("""
                SELECT outcome FROM regional_payment_execution
                WHERE institution_code=? AND idempotency_key=?
                """, String.class, FIC, "R6610-FAIL"));
        assertEquals(PaymentExecutionOutcome.UNKNOWN,
                persistence.findByPaymentReference(FIC, cmd.paymentReference()).orElseThrow().outcome());
        assertThrows(RuntimeException.class, () -> persistence.executeIdempotent(
                FIC, "R6610-FAIL", "b".repeat(64), cmd,
                () -> { throw new AssertionError("Financial replay"); }));
    }
}
