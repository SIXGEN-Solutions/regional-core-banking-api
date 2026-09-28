package com.regional.corebanking.confirmation.application.service;
import com.regional.corebanking.confirmation.application.exception.ConfirmationRejectedException;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.*;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class PaymentConfirmationRobustnessTest {
 private static final Instant NOW=Instant.parse("2026-09-28T16:00:00Z");
 @Test void concurrentInvalidOtpAttemptsCannotLoseUpdates() throws Exception {
  var f=fixture(Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofMinutes(5));
  var c=f.service.create("REGIONAL","create-race-01",command());
  ExecutorService pool=Executors.newFixedThreadPool(3);
  try {
   CountDownLatch start=new CountDownLatch(1);
   List<Callable<ConfirmationChallenge>> tasks=new ArrayList<>();
   for(int i=0;i<3;i++){final int n=i;tasks.add(()->{start.await();return f.service.verify("REGIONAL","verify-race-"+n,c.challengeReference(),new ConfirmationCommand.Verify(c.paymentReference(),"999999".toCharArray()));});}
   var futures=tasks.stream().map(pool::submit).toList(); start.countDown();
   for(var x:futures)x.get(5,TimeUnit.SECONDS);
   var current=f.service.get("REGIONAL",c.challengeReference());
   assertThat(current.failedAttempts()).isEqualTo(3);
   assertThat(current.status()).isEqualTo(ChallengeStatus.LOCKED);
  } finally {pool.shutdownNow();}
 }
 @Test void concurrentReplacementAllowsOnlyOneReplacementOfOriginalChallenge() throws Exception {
  var f=fixture(Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofMinutes(5));
  var c=f.service.create("REGIONAL","create-replace-race",command());
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try {
   CountDownLatch start=new CountDownLatch(1);
   Callable<Object> task=()->{start.await();try{return f.service.replace("REGIONAL",UUID.randomUUID().toString(),c.challengeReference(),new ConfirmationCommand.Replace(c.paymentReference()));}catch(ConfirmationRejectedException e){return e;}};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();
   var results=List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));
   assertThat(results.stream().filter(ConfirmationChallenge.class::isInstance).count()).isEqualTo(1);
   assertThat(results.stream().filter(ConfirmationRejectedException.class::isInstance).count()).isEqualTo(1);
   assertThat(f.service.get("REGIONAL",c.challengeReference()).status()).isEqualTo(ChallengeStatus.REPLACED);
  } finally {pool.shutdownNow();}
 }
 @Test void verificationAtExpiryBoundaryCannotVerifyExpiredChallenge(){
  var f=fixture(Clock.fixed(NOW,ZoneOffset.UTC),Duration.ZERO);
  var c=f.service.create("REGIONAL","create-expiry-race",command());
  var r=f.service.verify("REGIONAL","verify-expiry-race",c.challengeReference(),new ConfirmationCommand.Verify(c.paymentReference(),f.delivered.get().clone()));
  assertThat(r.status()).isEqualTo(ChallengeStatus.EXPIRED);assertThat(r.verifiedAt()).isNull();
 }
 @Test void unknownDeliveryOutcomeRemainsExplicit(){
  AtomicReference<char[]> d=new AtomicReference<>();
  var s=new PaymentConfirmationService(new InMemoryChallengeRepository(),new InMemoryIdempotencyRepository(),new HmacOtpSecurityAdapter(new byte[32],"test-v1"),delivery(ConfirmationDeliveryPort.Outcome.UNKNOWN,d),Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofMinutes(5),3,3);
  var r=s.create("REGIONAL","unknown-delivery-01",command());
  assertThat(r.deliveryStatus()).isEqualTo(DeliveryStatus.UNKNOWN);
  assertThat(r.businessCode()).isEqualTo(ConfirmationBusinessCode.DEPENDENCY_RESULT_UNKNOWN);
  assertThat(r.sentAt()).isNull();assertThat(r.deliveryRequestedAt()).isNotNull();
 }
 private static Fixture fixture(Clock c,Duration ttl){AtomicReference<char[]> d=new AtomicReference<>();return new Fixture(new PaymentConfirmationService(new InMemoryChallengeRepository(),new InMemoryIdempotencyRepository(),new HmacOtpSecurityAdapter(new byte[32],"test-v1"),delivery(ConfirmationDeliveryPort.Outcome.ACCEPTED,d),c,ttl,3,3),d);}
 private static ConfirmationDeliveryPort delivery(ConfirmationDeliveryPort.Outcome o,AtomicReference<char[]> d){return new ConfirmationDeliveryPort(){public Set<DeliveryChannel> enabledChannels(){return Set.of(DeliveryChannel.EMAIL);}public Outcome dispatch(String i,ConfirmationChallenge c,char[] otp){d.set(otp.clone());return o;}};}
 private static ConfirmationCommand.Create command(){return new ConfirmationCommand.Create("PAY-0123456789ABCDEFGHJKMNPQRS","CUS-001","001-123456-7",new BigDecimal("1000.00"),"XAF");}
 private record Fixture(PaymentConfirmationService service,AtomicReference<char[]> delivered){}
}
