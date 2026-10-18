package com.regional.corebanking.payment.infrastructure.amplitude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regional.corebanking.payment.domain.*;
import com.regional.corebanking.payment.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual production banking adapter using Oracle Free and a disposable PG database.
 * Test Oracle schema is synthetic, not authoritative Amplitude evidence.
 */
@EnabledIfEnvironmentVariable(named = "RUN_R6610_ORACLE_IT", matches = "true")
class R6610OraclePaymentIT {
    private static final String FIC="REGIONAL", OPE="582";
    private JdbcTemplate pg, oracle;
    private JdbcPaymentExecutionPersistenceAdapter repository;
    private JdbcPaymentExecutionBankingAdapter banking;

    // The existing shared dialect uses the Informix-style '%Y' format.
    // Override ONLY in Oracle mock test infrastructure; no production SQL change.
    private static final class OracleMockDialect extends AbstractPaymentExecutionSqlDialect {
        @Override public String findHolidays() {
            return "SELECT jourfer FROM bkfer WHERE TO_CHAR(jourfer, 'YYYY') = ?";
        }
    }

    @BeforeEach void prepare() throws Exception {
        String pgUrl=System.getenv("REGIONAL_CONFIRMATION_IT_DB_URL");
        assertNotNull(pgUrl,"Dedicated PostgreSQL test URL required");
        assertTrue(pgUrl.toLowerCase(Locale.ROOT).matches(
            "jdbc:postgresql://[^/]+/regional_core_banking_test(?:\\?.*)?"),
            "REFUSING non-disposable PostgreSQL DB");
        String oracleUrl=System.getenv().getOrDefault("ORACLE_JDBC_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        assertTrue(oracleUrl.matches("jdbc:oracle:thin:@//(?:localhost|127\\.0\\.0\\.1):[0-9]+/FREEPDB1"),
            "Only localhost Oracle Free test DB permitted");
        DataSource pds=new DriverManagerDataSource(pgUrl,
            System.getenv("REGIONAL_CONFIRMATION_IT_DB_USERNAME"),
            System.getenv("REGIONAL_CONFIRMATION_IT_DB_PASSWORD"));
        DataSource ods=new DriverManagerDataSource(oracleUrl,"REGIONAL_MOCK",System.getenv("ORACLE_APP_PASSWORD"));
        pg=new JdbcTemplate(pds); oracle=new JdbcTemplate(ods);
        pg.execute("DROP TABLE IF EXISTS regional_payment_execution");
        String migration=Files.readString(Path.of("src/main/resources/db/migration/V2__create_payment_execution_store.sql"));
        for(String sql:migration.split(";")) if(!sql.isBlank())pg.execute(sql);
        pg.execute("DROP TABLE IF EXISTS api_eodprocess");
        pg.execute("CREATE TABLE api_eodprocess (id varchar(64) primary key, lasteve varchar(6), version bigint not null default 0)");
        oracle.update("DELETE FROM BKEVE WHERE OPE = ?",OPE);
        oracle.update("DELETE FROM BKEVE_EOD WHERE OPE = ?",OPE);
        oracle.update("UPDATE BKCOM SET SIN=10000 WHERE NCP='123456'");
        oracle.update("UPDATE BKCOM SET SIN=500 WHERE NCP='654321'");
        oracle.update("DELETE FROM BKOPPCLI");
        oracle.update("DELETE FROM BKOPPCOM");
        oracle.update("DELETE FROM BKOPL");
        DataSourceTransactionManager manager=new DataSourceTransactionManager(pds);
        repository=new JdbcPaymentExecutionPersistenceAdapter(pg,manager,new ObjectMapper().registerModule(new JavaTimeModule()));
        banking=new JdbcPaymentExecutionBankingAdapter(ods,new OracleMockDialect(),FIC,
            BkeveIndicativeBalanceUpdate.unconfigured(),
            new JdbcPaymentEventNumberReservationAdapter(pg,manager),
            new JdbcPaymentBankEventTrackingAdapter(pg));
    }

    private PaymentExecutionCommand command(String ref,BigDecimal amount) {
        return new PaymentExecutionCommand(ref,"v1",
            new PaymentProviderEvent(ref,OPE,"XAF","PAYMENT","TEST",
                "001-123456-7","001-654321-8",amount,amount,"Oracle simulated payment",
                "001","001","ORACLE-MOCK"),OffsetDateTime.parse("2026-10-19T00:00:00Z"));
    }

    @Test void paymentWrites233ColumnsAndBalancesAndRecovers() {
        var cmd=command("PAY-R6610-ORACLE-OK",new BigDecimal("100"));
        var first=repository.executeIdempotent(FIC,"oracle-test-ok","a".repeat(64),cmd,
            ()->banking.execute(FIC,"oracle-test-ok",cmd));
        assertEquals(PaymentExecutionOutcome.COMPLETED,first.outcome());
        assertEquals(1,oracle.queryForObject("SELECT COUNT(*) FROM BKEVE WHERE OPE=? AND EVE=?",
            Integer.class,OPE,first.bankReference().split("/")[1]));
        assertEquals(0,new BigDecimal("9900").compareTo(oracle.queryForObject(
            "SELECT SIN FROM BKCOM WHERE NCP='123456'",BigDecimal.class)));
        assertEquals(0,new BigDecimal("600").compareTo(oracle.queryForObject(
            "SELECT SIN FROM BKCOM WHERE NCP='654321'",BigDecimal.class)));
        assertEquals(1L,pg.queryForObject("SELECT event_number FROM regional_payment_execution WHERE idempotency_key='oracle-test-ok'",Long.class));
        assertEquals("000001",pg.queryForObject("SELECT lasteve FROM api_eodprocess WHERE id=?",String.class,OPE));
        assertEquals(first.bankReference(),banking.findByPaymentReference(FIC,cmd.paymentReference()).orElseThrow().bankReference());
        assertEquals(first.bankReference(),banking.findByIdempotencyKey(FIC,"oracle-test-ok").orElseThrow().bankReference());
        var replay=repository.executeIdempotent(FIC,"oracle-test-ok","a".repeat(64),cmd,
            ()-> {throw new AssertionError("Illegal replay");});
        assertEquals(first,replay);
        assertEquals(1,oracle.queryForObject("SELECT COUNT(*) FROM BKEVE WHERE OPE=?",Integer.class,OPE));
    }

    @Test void insufficientFundsRejectsBeforeReservation() {
        var cmd=command("PAY-R6610-ORACLE-REJECT",new BigDecimal("20000"));
        var result=repository.executeIdempotent(FIC,"oracle-test-reject","b".repeat(64),cmd,
            ()->banking.execute(FIC,"oracle-test-reject",cmd));
        assertEquals(PaymentExecutionOutcome.REJECTED,result.outcome());
        assertEquals(0,oracle.queryForObject("SELECT COUNT(*) FROM BKEVE WHERE OPE=?",Integer.class,OPE));
        assertEquals(0,pg.queryForObject("SELECT COUNT(*) FROM api_eodprocess",Integer.class));
    }

    @Test void failedCreditUpdateRollsBackOracleAndKeepsPgUnknownAndEveReservation() {
        // No credit-side row -> UPDATE affects zero -> rollback after BKEVE insert.
        oracle.update("DELETE FROM BKCOM WHERE NCP='654321'");
        var cmd=command("PAY-R6610-ORACLE-ROLLBACK",new BigDecimal("100"));
        try {
            assertThrows(RuntimeException.class,()-> repository.executeIdempotent(FIC,
                "oracle-test-rollback","c".repeat(64),cmd,
                ()->banking.execute(FIC,"oracle-test-rollback",cmd)));
            assertEquals(0,oracle.queryForObject("SELECT COUNT(*) FROM BKEVE WHERE OPE=?",Integer.class,OPE));
            assertEquals(0,new BigDecimal("10000").compareTo(oracle.queryForObject(
                "SELECT SIN FROM BKCOM WHERE NCP='123456'",BigDecimal.class)));
            assertEquals("UNKNOWN",pg.queryForObject("SELECT outcome FROM regional_payment_execution WHERE idempotency_key='oracle-test-rollback'",String.class));
            assertEquals("000001",pg.queryForObject("SELECT lasteve FROM api_eodprocess WHERE id=?",String.class,OPE));
        } finally {
            oracle.update("INSERT INTO BKCOM (AGE,NCP,CLC,CLI,DEV,IFE,CFE,SIN,MINDS) VALUES ('001','654321','8','CUS-002','001','N','N',500,0)");
        }
    }
}
