package com.regional.corebanking.confirmation.application.service;

import com.regional.corebanking.confirmation.application.exception.*;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.application.port.out.*;
import com.regional.corebanking.confirmation.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class PaymentConfirmationService implements PaymentConfirmationUseCase {
    private final ChallengeRepository challenges;
    private final IdempotencyRepository idempotency;
    private final OtpSecurityPort otp;
    private final ConfirmationDeliveryPort delivery;
    private final Clock clock;
    private final Duration ttl;
    private final int maxAttempts;
    private final int maxReplacements;

    public PaymentConfirmationService(ChallengeRepository challenges, IdempotencyRepository idempotency,
            OtpSecurityPort otp, ConfirmationDeliveryPort delivery, Clock clock, Duration ttl,
            int maxAttempts, int maxReplacements) {
        this.challenges=challenges; this.idempotency=idempotency; this.otp=otp; this.delivery=delivery;
        this.clock=clock; this.ttl=ttl; this.maxAttempts=maxAttempts; this.maxReplacements=maxReplacements;
    }

    public ConfirmationChallenge create(String institution,String key,ConfirmationCommand.Create c) {
        String fp=fingerprint("CREATE",institution,c.paymentReference(),c.customerReference(),
                c.debtorAccountReference(),c.amount().toPlainString(),c.currency());
        return idempotent(key,"CREATE",fp,()->{
            Instant now=clock.instant(); String ref="CHL-"+UUID.randomUUID(); char[] value=otp.generate();
            try {
                var challenge=new ConfirmationChallenge(ref,c.paymentReference(),c.customerReference(),
                        c.debtorAccountReference(),c.amount(),c.currency(),otp.verifier(ref,value),otp.keyVersion(),
                        ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE,0,0,
                        delivery.enabledChannels(),now,now.plus(ttl),null,null,null);
                challenges.save(challenge); delivery.dispatch(challenge,value); return challenge;
            } finally { Arrays.fill(value,'\0'); }
        });
    }

    public ConfirmationChallenge verify(String institution,String key,String ref,ConfirmationCommand.Verify c) {
        String fp=fingerprint("VERIFY",institution,ref,c.paymentReference(),otp.verifier("IDEMPOTENCY:"+ref,c.otp()));
        try {
            return idempotent(key,"VERIFY",fp,()->challenges.update(ref,current->{
                requirePayment(current,c.paymentReference()); current=expire(current);
                if(current.status()!=ChallengeStatus.ACTIVE) return current;
                if(otp.matches(ref,c.otp(),current.otpVerifier())) return current.verified(clock.instant());
                int attempts=current.failedAttempts()+1;
                return attempts>=maxAttempts
                        ? current.failed(attempts,ChallengeStatus.LOCKED,ConfirmationBusinessCode.CHALLENGE_LOCKED)
                        : current.failed(attempts,ChallengeStatus.ACTIVE,ConfirmationBusinessCode.OTP_INVALID);
            }));
        } finally { Arrays.fill(c.otp(),'\0'); }
    }

    public ConfirmationChallenge replace(String institution,String key,String ref,ConfirmationCommand.Replace c) {
        String fp=fingerprint("REPLACE",institution,ref,c.paymentReference());
        return idempotent(key,"REPLACE",fp,()->{
            var old=challenges.update(ref,current->{
                requirePayment(current,c.paymentReference()); current=expire(current);
                if(current.status()!=ChallengeStatus.ACTIVE) throw new ConfirmationRejectedException("Only ACTIVE challenge can be replaced");
                if(current.replacementCount()>=maxReplacements) throw new ConfirmationRejectedException("Replacement maximum reached");
                return current.replaced(clock.instant());
            });
            char[] value=otp.generate();
            try {
                Instant now=clock.instant(); String newRef="CHL-"+UUID.randomUUID();
                var replacement=new ConfirmationChallenge(newRef,old.paymentReference(),old.customerReference(),
                        old.debtorAccountReference(),old.amount(),old.currency(),otp.verifier(newRef,value),otp.keyVersion(),
                        ChallengeStatus.ACTIVE,ConfirmationBusinessCode.CHALLENGE_ACTIVE,0,old.replacementCount()+1,
                        delivery.enabledChannels(),now,now.plus(ttl),null,null,null);
                challenges.save(replacement); delivery.dispatch(replacement,value); return replacement;
            } finally { Arrays.fill(value,'\0'); }
        });
    }

    public ConfirmationChallenge get(String institution,String ref) { return challenges.update(ref,this::expire); }

    public ConfirmationChallenge recover(String institution,String key) {
        return idempotency.find(key).map(IdempotencyRepository.Entry::result)
                .orElseThrow(()->new ChallengeNotFoundException("idempotency:"+key));
    }

    public ConfirmationChallenge revoke(String institution,String key,String ref,ConfirmationCommand.Revoke c) {
        String fp=fingerprint("REVOKE",institution,ref,c.paymentReference(),c.reasonCode());
        return idempotent(key,"REVOKE",fp,()->challenges.update(ref,current->{
            requirePayment(current,c.paymentReference()); current=expire(current);
            if(current.status()==ChallengeStatus.REVOKED) return current;
            if(current.status()!=ChallengeStatus.ACTIVE) throw new ConfirmationRejectedException("Only ACTIVE challenge can be revoked");
            return current.revoked(clock.instant());
        }));
    }

    private ConfirmationChallenge expire(ConfirmationChallenge c) {
        return c.status()==ChallengeStatus.ACTIVE && !clock.instant().isBefore(c.expiresAt()) ? c.expired() : c;
    }
    private static void requirePayment(ConfirmationChallenge c,String payment) {
        if(!Objects.equals(c.paymentReference(),payment)) throw new ConfirmationRejectedException("Challenge is not bound to this payment");
    }
    private ConfirmationChallenge idempotent(String key,String operation,String fp,Supplier<ConfirmationChallenge> action) {
        var existing=idempotency.find(key);
        if(existing.isPresent()) {
            var e=existing.get();
            if(!e.operation().equals(operation) || !MessageDigest.isEqual(
                    e.fingerprint().getBytes(StandardCharsets.US_ASCII),fp.getBytes(StandardCharsets.US_ASCII)))
                throw new IdempotencyConflictException();
            return e.result();
        }
        var result=action.get(); idempotency.save(new IdempotencyRepository.Entry(key,operation,fp,result)); return result;
    }
    private static String fingerprint(String... values) {
        try {
            var d=MessageDigest.getInstance("SHA-256");
            for(String v:values) { byte[] b=Objects.toString(v,"").getBytes(StandardCharsets.UTF_8); d.update(b); d.update((byte)0); }
            return HexFormat.of().formatHex(d.digest());
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
