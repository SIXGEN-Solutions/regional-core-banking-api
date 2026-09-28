package com.regional.corebanking.confirmation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.regional.corebanking.confirmation.application.exception.IdempotencyConflictException;
import com.regional.corebanking.confirmation.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JdbcConfirmationPersistenceTest {
    private JdbcTemplate jdbc; private TransactionTemplate tx; private ObjectMapper json;
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("REGIONAL_CONFIRMATION_IT_DB_URL");
        Assumptions.assumeTrue(url!=null && !url.isBlank(), "PostgreSQL integration DB not configured");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv("REGIONAL_CONFIRMATION_IT_DB_USERNAME"),System.getenv("REGIONAL_CONFIRMATION_IT_DB_PASSWORD"));
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        json=new ObjectMapper().registerModule(new JavaTimeModule());
        String ddl=Files.readString(Path.of("src/main/resources/db/r5_2_1/confirmation-postgresql.sql"));
        for(String statement:ddl.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        jdbc.update("DELETE FROM regional_confirmation_idempotency"); jdbc.update("DELETE FROM regional_confirmation_challenge");
    }
    @Test void recoverySurvivesRepositoryRecreation(){
        JdbcIdempotencyRepository first=new JdbcIdempotencyRepository(jdbc,tx,json);
        ConfirmationChallenge c=challenge("CHL-RECOVERY");
        first.execute("REGIONAL","idem-recovery","CREATE","a".repeat(64),()->c);
        JdbcIdempotencyRepository restarted=new JdbcIdempotencyRepository(jdbc,tx,json);
        assertEquals(c,restarted.find("REGIONAL","idem-recovery").orElseThrow().result());
    }
    @Test void sameKeyAcrossTwoInstancesExecutesActionOnce() throws Exception {
        JdbcIdempotencyRepository a=new JdbcIdempotencyRepository(jdbc,tx,json), b=new JdbcIdempotencyRepository(jdbc,tx,json);
        AtomicInteger calls=new AtomicInteger(); ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1); Callable<ConfirmationChallenge> action=()->{start.await();return a.execute("REGIONAL","idem-concurrent","CREATE","b".repeat(64),()->{calls.incrementAndGet();return challenge("CHL-CONCURRENT");});};
        Future<ConfirmationChallenge> f1=pool.submit(action);
        Future<ConfirmationChallenge> f2=pool.submit(()->{start.await();return b.execute("REGIONAL","idem-concurrent","CREATE","b".repeat(64),()->{calls.incrementAndGet();return challenge("CHL-CONCURRENT-2");});});
        start.countDown(); assertEquals(f1.get(),f2.get()); assertEquals(1,calls.get()); pool.shutdownNow();
    }
    @Test void idempotencyIsScopedByInstitution(){
        JdbcIdempotencyRepository r=new JdbcIdempotencyRepository(jdbc,tx,json);
        r.execute("BANK-A","same-key","CREATE","c".repeat(64),()->challenge("CHL-A"));
        r.execute("BANK-B","same-key","CREATE","d".repeat(64),()->challenge("CHL-B"));
        assertEquals("CHL-A",r.find("BANK-A","same-key").orElseThrow().result().challengeReference());
        assertEquals("CHL-B",r.find("BANK-B","same-key").orElseThrow().result().challengeReference());
    }
    @Test void sameScopedKeyDifferentFingerprintConflicts(){
        JdbcIdempotencyRepository r=new JdbcIdempotencyRepository(jdbc,tx,json);
        r.execute("REGIONAL","conflict-key","CREATE","e".repeat(64),()->challenge("CHL-X"));
        assertThrows(IdempotencyConflictException.class,()->r.execute("REGIONAL","conflict-key","CREATE","f".repeat(64),()->challenge("CHL-Y")));
    }
    private static ConfirmationChallenge challenge(String ref){Instant now=Instant.parse("2026-09-27T12:00:00Z");return new ConfirmationChallenge(ref,"PAY-0123456789ABCDEFGHJKMNPQRS","C1","001-1-1",new BigDecimal("100.00"),"XAF","verifier","v1",ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE,0,0,Set.of(DeliveryChannel.EMAIL),now,now.plusSeconds(300),null,null,null);}
}
