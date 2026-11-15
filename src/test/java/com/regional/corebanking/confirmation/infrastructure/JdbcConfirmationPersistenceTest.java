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
        assertDedicatedIntegrationDatabase(url);
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
    @Test
    void challengeStateSurvivesRepositoryRecreation() {
        JdbcChallengeRepository first = new JdbcChallengeRepository(jdbc, tx);

        ConfirmationChallenge original = challenge("CHL-RESTART")
                .deliveryRequested(Instant.parse("2026-09-27T12:00:01Z"))
                .deliveryAccepted(Instant.parse("2026-09-27T12:00:02Z"));

        first.save("REGIONAL", original);

        JdbcChallengeRepository restarted =
                new JdbcChallengeRepository(jdbc, tx);

        ConfirmationChallenge actual =
                restarted.find("REGIONAL", "CHL-RESTART").orElseThrow();

        assertEquals(original.challengeReference(), actual.challengeReference());
        assertEquals(original.paymentReference(), actual.paymentReference());
        assertEquals(original.customerReference(), actual.customerReference());
        assertEquals(original.debtorAccountReference(), actual.debtorAccountReference());

        assertEquals(
                0,
                original.amount().compareTo(actual.amount()),
                "Persisted amount must remain numerically equal regardless of BigDecimal scale"
        );

        assertEquals(original.currency(), actual.currency());
        assertEquals(original.otpVerifier(), actual.otpVerifier());
        assertEquals(original.otpKeyVersion(), actual.otpKeyVersion());
        assertEquals(original.status(), actual.status());
        assertEquals(original.businessCode(), actual.businessCode());
        assertEquals(original.failedAttempts(), actual.failedAttempts());
        assertEquals(original.replacementCount(), actual.replacementCount());
        assertEquals(original.deliveryChannels(), actual.deliveryChannels());
        assertEquals(original.createdAt(), actual.createdAt());
        assertEquals(original.expiresAt(), actual.expiresAt());
        assertEquals(original.verifiedAt(), actual.verifiedAt());
        assertEquals(original.replacedAt(), actual.replacedAt());
        assertEquals(original.revokedAt(), actual.revokedAt());
        assertEquals(original.deliveryStatus(), actual.deliveryStatus());
        assertEquals(original.deliveryRequestedAt(), actual.deliveryRequestedAt());
        assertEquals(original.sentAt(), actual.sentAt());
    }
    @Test void concurrentChallengeUpdatesAcrossRepositoryInstancesDoNotLoseOtpAttempts() throws Exception {
        JdbcChallengeRepository a=new JdbcChallengeRepository(jdbc,tx), b=new JdbcChallengeRepository(jdbc,tx);
        a.save("REGIONAL",challenge("CHL-OTP-RACE"));
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<ConfirmationChallenge> one=()->{start.await();return a.update("REGIONAL","CHL-OTP-RACE",c->c.failed(c.failedAttempts()+1,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.OTP_INVALID));};
        Callable<ConfirmationChallenge> two=()->{start.await();return b.update("REGIONAL","CHL-OTP-RACE",c->c.failed(c.failedAttempts()+1,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.OTP_INVALID));};
        Future<ConfirmationChallenge> f1=pool.submit(one),f2=pool.submit(two);start.countDown();f1.get(5,TimeUnit.SECONDS);f2.get(5,TimeUnit.SECONDS);
        assertEquals(2,a.find("REGIONAL","CHL-OTP-RACE").orElseThrow().failedAttempts());pool.shutdownNow();
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
    private static void assertDedicatedIntegrationDatabase(String url) {
        String normalized = url.toLowerCase(java.util.Locale.ROOT);
        boolean dedicatedDatabase = normalized.matches(
                "jdbc:postgresql://[^/]+/regional_core_banking_test(?:\\?.*)?"
        );

        if (!dedicatedDatabase) {
            throw new IllegalStateException(
                    "Refusing to run destructive confirmation persistence integration tests "
                            + "against a non-test database. REGIONAL_CONFIRMATION_IT_DB_URL "
                            + "must target database 'regional_core_banking_test'. Actual URL: "
                            + url
            );
        }
    }

    private static ConfirmationChallenge challenge(String ref){Instant now=Instant.parse("2026-09-27T12:00:00Z");return new ConfirmationChallenge(ref,"PAY-0123456789ABCDEFGHJKMNPQRS","C1","001-1-1",new BigDecimal("100.00"),"XAF","verifier","v1",ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE,0,0,Set.of(DeliveryChannel.EMAIL),now,now.plusSeconds(300),null,null,null);}
}
