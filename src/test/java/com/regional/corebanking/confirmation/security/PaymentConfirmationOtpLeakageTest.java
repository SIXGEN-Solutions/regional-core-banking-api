package com.regional.corebanking.confirmation.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.api.PaymentConfirmationApiMapper;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.service.PaymentConfirmationService;
import com.regional.corebanking.confirmation.domain.*;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
class PaymentConfirmationOtpLeakageTest {
 @Test void otpIsAbsentFromDomainStringAndApiResponse() throws Exception {
  AtomicReference<char[]> d=new AtomicReference<>();
  ConfirmationDeliveryPort delivery=new ConfirmationDeliveryPort(){public Set<DeliveryChannel> enabledChannels(){return Set.of(DeliveryChannel.EMAIL);}public Outcome dispatch(String i,ConfirmationChallenge c,char[] otp){d.set(otp.clone());return Outcome.ACCEPTED;}};
  var service=new PaymentConfirmationService(new InMemoryChallengeRepository(),new InMemoryIdempotencyRepository(),new HmacOtpSecurityAdapter(new byte[32],"test-v1"),delivery,Clock.fixed(Instant.parse("2026-09-28T16:00:00Z"),ZoneOffset.UTC),Duration.ofMinutes(5),3,3);
  var c=service.create("REGIONAL","leakage-create-01",new ConfirmationCommand.Create("PAY-0123456789ABCDEFGHJKMNPQRS","CUS-001","001-123456-7",new BigDecimal("1000.00"),"XAF"));
  String otp=new String(d.get());assertThat(c.toString()).doesNotContain(otp);
  ObjectMapper m=new ObjectMapper().findAndRegisterModules();
  String response=m.writeValueAsString(new PaymentConfirmationApiMapper(m).toChallengeResult(c));
  assertThat(response).doesNotContain(otp).doesNotContain(c.otpVerifier());
 }
}
