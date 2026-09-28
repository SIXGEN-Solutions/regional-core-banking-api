package com.regional.corebanking.confirmation.application.service;
import com.regional.corebanking.confirmation.application.exception.IdempotencyConflictException;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.*;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class PaymentConfirmationServiceTest {
    private final Instant now=Instant.parse("2026-09-27T18:00:00Z");
    private final InMemoryChallengeRepository challenges=new InMemoryChallengeRepository();
    private final InMemoryIdempotencyRepository idem=new InMemoryIdempotencyRepository();
    private final HmacOtpSecurityAdapter otp=new HmacOtpSecurityAdapter(new byte[32],"test-v1");
    private final AtomicReference<char[]> delivered=new AtomicReference<>();

    private final ConfirmationDeliveryPort delivery = new ConfirmationDeliveryPort() {
        public Set<DeliveryChannel> enabledChannels() {
            return Set.of(DeliveryChannel.SMS, DeliveryChannel.EMAIL);
        }

        public Outcome dispatch(String institution, ConfirmationChallenge c, char[] v) {
            delivered.set(v.clone());
            return Outcome.ACCEPTED;
        }
    };
    private final PaymentConfirmationService service=new PaymentConfirmationService(challenges,idem,otp,delivery,
            Clock.fixed(now,ZoneOffset.UTC),Duration.ofMinutes(5),3,3);
    private ConfirmationCommand.Create create(){ return new ConfirmationCommand.Create(
            "PAY-0123456789ABCDEFGHJKMNPQRS","CUS-001","001-123456-7",new BigDecimal("1000.00"),"XAF"); }

    @Test void createIsReplaySafeAndDoesNotStorePlainOtp(){
        var first=service.create("REGIONAL","create-key-0001",create());
        var replay=service.create("REGIONAL","create-key-0001",create());
        assertThat(replay.challengeReference()).isEqualTo(first.challengeReference());
        assertThat(first.otpVerifier()).doesNotContain(new String(delivered.get()));
        assertThat(first.deliveryChannels()).containsExactlyInAnyOrder(DeliveryChannel.SMS,DeliveryChannel.EMAIL);
    }
    @Test void sameKeyDifferentRequestConflicts(){
        service.create("REGIONAL","create-key-0002",create());
        var changed=new ConfirmationCommand.Create(create().paymentReference(),"CUS-002",
                create().debtorAccountReference(),create().amount(),create().currency());
        assertThatThrownBy(()->service.create("REGIONAL","create-key-0002",changed))
                .isInstanceOf(IdempotencyConflictException.class);
    }
    @Test void correctOtpVerifies(){
        var c=service.create("REGIONAL","create-key-0003",create());
        var r=service.verify("REGIONAL","verify-key-0001",c.challengeReference(),
                new ConfirmationCommand.Verify(c.paymentReference(),delivered.get().clone()));
        assertThat(r.status()).isEqualTo(ChallengeStatus.VERIFIED);
    }
    @Test void thirdInvalidAttemptLocks(){
        var c=service.create("REGIONAL","create-key-0004",create()); ConfirmationChallenge r=null;
        for(int i=1;i<=3;i++) r=service.verify("REGIONAL","bad-key-000"+i,c.challengeReference(),
                new ConfirmationCommand.Verify(c.paymentReference(),"000000".toCharArray()));
        assertThat(r.status()).isEqualTo(ChallengeStatus.LOCKED);
    }
    @Test void replacementInvalidatesOldChallenge(){
        var c=service.create("REGIONAL","create-key-0005",create());
        var r=service.replace("REGIONAL","replace-key-01",c.challengeReference(),
                new ConfirmationCommand.Replace(c.paymentReference()));
        assertThat(service.get("REGIONAL",c.challengeReference()).status()).isEqualTo(ChallengeStatus.REPLACED);
        assertThat(r.challengeReference()).isNotEqualTo(c.challengeReference());
    }
    @Test void revokeIsRecoverableByIdempotencyKey(){
        var c=service.create("REGIONAL","create-key-0006",create());
        service.revoke("REGIONAL","revoke-key-01",c.challengeReference(),
                new ConfirmationCommand.Revoke(c.paymentReference(),"PAYMENT_CANCELLED"));
        assertThat(service.recover("REGIONAL","revoke-key-01").status()).isEqualTo(ChallengeStatus.REVOKED);
    }
}
