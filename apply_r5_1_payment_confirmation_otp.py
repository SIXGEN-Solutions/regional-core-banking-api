#!/usr/bin/env python3
from pathlib import Path
import subprocess
import textwrap

EXPECTED_BRANCH = "feat/customer-account-verification"
EXPECTED_HEAD = "a6b694dd911afa1906721de2082764a2e36d012b"

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

branch = git("branch", "--show-current")
head = git("rev-parse", "HEAD")
if branch != EXPECTED_BRANCH:
    raise SystemExit(f"Expected branch {EXPECTED_BRANCH}, got {branch}")
if head != EXPECTED_HEAD:
    raise SystemExit(f"Expected HEAD {EXPECTED_HEAD}, got {head}")

files = {
"src/main/java/com/regional/corebanking/confirmation/domain/ChallengeStatus.java": """
package com.regional.corebanking.confirmation.domain;
public enum ChallengeStatus { ACTIVE, VERIFIED, EXPIRED, LOCKED, REPLACED, REVOKED }
""",
"src/main/java/com/regional/corebanking/confirmation/domain/ConfirmationBusinessCode.java": """
package com.regional.corebanking.confirmation.domain;
public enum ConfirmationBusinessCode {
    CHALLENGE_ACTIVE, OTP_VERIFIED, OTP_INVALID, CHALLENGE_EXPIRED, CHALLENGE_LOCKED,
    CHALLENGE_REPLACED, CHALLENGE_REVOKED, RESEND_NOT_ALLOWED,
    CONFIRMATION_NOT_AVAILABLE, DEPENDENCY_RESULT_UNKNOWN, DELIVERY_FAILED
}
""",
"src/main/java/com/regional/corebanking/confirmation/domain/DeliveryChannel.java": """
package com.regional.corebanking.confirmation.domain;
public enum DeliveryChannel { SMS, EMAIL }
""",
"src/main/java/com/regional/corebanking/confirmation/domain/ConfirmationChallenge.java": """
package com.regional.corebanking.confirmation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record ConfirmationChallenge(
        String challengeReference, String paymentReference, String customerReference,
        String debtorAccountReference, BigDecimal amount, String currency,
        String otpVerifier, String otpKeyVersion, ChallengeStatus status,
        ConfirmationBusinessCode businessCode, int failedAttempts, int replacementCount,
        Set<DeliveryChannel> deliveryChannels, Instant createdAt, Instant expiresAt,
        Instant verifiedAt, Instant replacedAt, Instant revokedAt) {
    public ConfirmationChallenge {
        deliveryChannels = deliveryChannels == null ? Set.of() : Set.copyOf(deliveryChannels);
    }
    public ConfirmationChallenge failed(int attempts, ChallengeStatus s, ConfirmationBusinessCode c) {
        return copy(s,c,attempts,replacementCount,verifiedAt,replacedAt,revokedAt);
    }
    public ConfirmationChallenge verified(Instant at) {
        return copy(ChallengeStatus.VERIFIED, ConfirmationBusinessCode.OTP_VERIFIED,
                failedAttempts,replacementCount,at,replacedAt,revokedAt);
    }
    public ConfirmationChallenge expired() {
        return copy(ChallengeStatus.EXPIRED, ConfirmationBusinessCode.CHALLENGE_EXPIRED,
                failedAttempts,replacementCount,verifiedAt,replacedAt,revokedAt);
    }
    public ConfirmationChallenge replaced(Instant at) {
        return copy(ChallengeStatus.REPLACED, ConfirmationBusinessCode.CHALLENGE_REPLACED,
                failedAttempts,replacementCount,verifiedAt,at,revokedAt);
    }
    public ConfirmationChallenge revoked(Instant at) {
        return copy(ChallengeStatus.REVOKED, ConfirmationBusinessCode.CHALLENGE_REVOKED,
                failedAttempts,replacementCount,verifiedAt,replacedAt,at);
    }
    private ConfirmationChallenge copy(ChallengeStatus s, ConfirmationBusinessCode c, int attempts,
                                       int replacements, Instant verified, Instant replaced, Instant revoked) {
        return new ConfirmationChallenge(challengeReference,paymentReference,customerReference,
                debtorAccountReference,amount,currency,otpVerifier,otpKeyVersion,s,c,attempts,
                replacements,deliveryChannels,createdAt,expiresAt,verified,replaced,revoked);
    }
}
""",
"src/main/java/com/regional/corebanking/confirmation/domain/ConfirmationCommand.java": """
package com.regional.corebanking.confirmation.domain;
import java.math.BigDecimal;
public final class ConfirmationCommand {
    private ConfirmationCommand() {}
    public record Create(String paymentReference,String customerReference,String debtorAccountReference,
                         BigDecimal amount,String currency) {}
    public record Verify(String paymentReference,char[] otp) {}
    public record Replace(String paymentReference) {}
    public record Revoke(String paymentReference,String reasonCode) {}
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/port/in/PaymentConfirmationUseCase.java": """
package com.regional.corebanking.confirmation.application.port.in;
import com.regional.corebanking.confirmation.domain.*;
public interface PaymentConfirmationUseCase {
    ConfirmationChallenge create(String institution,String key,ConfirmationCommand.Create command);
    ConfirmationChallenge verify(String institution,String key,String reference,ConfirmationCommand.Verify command);
    ConfirmationChallenge replace(String institution,String key,String reference,ConfirmationCommand.Replace command);
    ConfirmationChallenge get(String institution,String reference);
    ConfirmationChallenge recover(String institution,String key);
    ConfirmationChallenge revoke(String institution,String key,String reference,ConfirmationCommand.Revoke command);
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/port/out/ChallengeRepository.java": """
package com.regional.corebanking.confirmation.application.port.out;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.function.UnaryOperator;
public interface ChallengeRepository {
    ConfirmationChallenge save(ConfirmationChallenge challenge);
    Optional<ConfirmationChallenge> find(String reference);
    ConfirmationChallenge update(String reference, UnaryOperator<ConfirmationChallenge> mutation);
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/port/out/IdempotencyRepository.java": """
package com.regional.corebanking.confirmation.application.port.out;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
public interface IdempotencyRepository {
    Optional<Entry> find(String key);
    void save(Entry entry);
    record Entry(String key,String operation,String fingerprint,ConfirmationChallenge result) {}
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/port/out/OtpSecurityPort.java": """
package com.regional.corebanking.confirmation.application.port.out;
public interface OtpSecurityPort {
    char[] generate();
    String verifier(String context,char[] otp);
    boolean matches(String context,char[] candidate,String expectedVerifier);
    String keyVersion();
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/port/out/ConfirmationDeliveryPort.java": """
package com.regional.corebanking.confirmation.application.port.out;
import com.regional.corebanking.confirmation.domain.*;
import java.util.Set;
public interface ConfirmationDeliveryPort {
    Set<DeliveryChannel> enabledChannels();
    void dispatch(ConfirmationChallenge challenge,char[] otp);
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/exception/ChallengeNotFoundException.java": """
package com.regional.corebanking.confirmation.application.exception;
public class ChallengeNotFoundException extends RuntimeException {
    public ChallengeNotFoundException(String reference) { super("Challenge not found: " + reference); }
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/exception/IdempotencyConflictException.java": """
package com.regional.corebanking.confirmation.application.exception;
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Idempotency-Key already used for a different logical request"); }
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/exception/ConfirmationRejectedException.java": """
package com.regional.corebanking.confirmation.application.exception;
public class ConfirmationRejectedException extends RuntimeException {
    public ConfirmationRejectedException(String message) { super(message); }
}
""",
"src/main/java/com/regional/corebanking/confirmation/application/service/PaymentConfirmationService.java": """
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
            } finally { Arrays.fill(value,'\\0'); }
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
        } finally { Arrays.fill(c.otp(),'\\0'); }
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
            } finally { Arrays.fill(value,'\\0'); }
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
""",
"src/main/java/com/regional/corebanking/confirmation/infrastructure/InMemoryChallengeRepository.java": """
package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
public final class InMemoryChallengeRepository implements ChallengeRepository {
    private final ConcurrentHashMap<String,ConfirmationChallenge> values=new ConcurrentHashMap<>();
    public ConfirmationChallenge save(ConfirmationChallenge c){ values.put(c.challengeReference(),c); return c; }
    public Optional<ConfirmationChallenge> find(String r){ return Optional.ofNullable(values.get(r)); }
    public ConfirmationChallenge update(String r,UnaryOperator<ConfirmationChallenge> mutation){
        if(!values.containsKey(r)) throw new ChallengeNotFoundException(r);
        return values.compute(r,(k,v)->mutation.apply(v));
    }
}
""",
"src/main/java/com/regional/corebanking/confirmation/infrastructure/InMemoryIdempotencyRepository.java": """
package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
public final class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String,Entry> values=new ConcurrentHashMap<>();
    public Optional<Entry> find(String key){ return Optional.ofNullable(values.get(key)); }
    public void save(Entry entry){ values.putIfAbsent(entry.key(),entry); }
}
""",
"src/main/java/com/regional/corebanking/confirmation/infrastructure/HmacOtpSecurityAdapter.java": """
package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.OtpSecurityPort;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class HmacOtpSecurityAdapter implements OtpSecurityPort {
    private final SecureRandom random=new SecureRandom();
    private final byte[] key; private final String version;
    public HmacOtpSecurityAdapter(byte[] key,String version){
        if(key==null || key.length<32) throw new IllegalArgumentException("OTP HMAC key must be at least 256 bits");
        this.key=key.clone(); this.version=version;
    }
    public char[] generate(){ return String.format("%06d",random.nextInt(1_000_000)).toCharArray(); }
    public String verifier(String context,char[] otp){
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            mac.update(context.getBytes(StandardCharsets.UTF_8)); mac.update((byte)0);
            ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(otp));
            byte[] b=new byte[encoded.remaining()]; encoded.get(b); mac.update(b); Arrays.fill(b,(byte)0);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch(Exception e){ throw new IllegalStateException("Unable to calculate OTP verifier",e); }
    }
    public boolean matches(String context,char[] candidate,String expected){
        return MessageDigest.isEqual(verifier(context,candidate).getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }
    public String keyVersion(){ return version; }
}
""",
"src/main/java/com/regional/corebanking/confirmation/infrastructure/NoopConfirmationDeliveryAdapter.java": """
package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.*;
import java.util.Set;
public final class NoopConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {
    private final Set<DeliveryChannel> channels;
    public NoopConfirmationDeliveryAdapter(Set<DeliveryChannel> channels){ this.channels=Set.copyOf(channels); }
    public Set<DeliveryChannel> enabledChannels(){ return channels; }
    public void dispatch(ConfirmationChallenge challenge,char[] otp){
        // R5.1 boundary only: no BKSMS/SMTP behavior without approved infrastructure evidence.
    }
}
""",
"src/test/java/com/regional/corebanking/confirmation/application/service/PaymentConfirmationServiceTest.java": """
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
    private final ConfirmationDeliveryPort delivery=new ConfirmationDeliveryPort(){
        public Set<DeliveryChannel> enabledChannels(){ return Set.of(DeliveryChannel.SMS,DeliveryChannel.EMAIL); }
        public void dispatch(ConfirmationChallenge c,char[] v){ delivered.set(v.clone()); }
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
""",
"src/test/java/com/regional/corebanking/confirmation/contract/PaymentConfirmationContractCompatibilityCharacterizationTest.java": """
package com.regional.corebanking.confirmation.contract;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class PaymentConfirmationContractCompatibilityCharacterizationTest {
    @Test void sixApprovedOperationsRemainInRegionalAndSixpayReference() throws Exception {
        String regional=Files.readString(Path.of("contracts/openapi/regional-core-banking-api-v1.yaml"));
        String sixpay=Files.readString(Path.of("contracts/reference/sixpay/amplitude-payment-confirmation-api-v1.yaml"));
        for(String path:new String[]{"/api/v1/payment-confirmation-challenges:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/verifications:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/replacements:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}:",
                "/api/v1/payment-confirmation-challenge-lookups/{idempotencyKey}:",
                "/api/v1/payment-confirmation-challenges/{challengeReference}/revocations:"}){
            assertThat(regional).contains(path); assertThat(sixpay).contains(path);
        }
    }
}
""",
"documentation/architecture/R5_1_PAYMENT_CONFIRMATION_OTP_BASELINE.md": """
# R5.1 — Payment Confirmation / OTP provider-independent baseline

Status: IMPLEMENTATION_BASELINE — this document does not approve a new contract.

R5.1 implements the provider-independent lifecycle behind the six already-approved Regional
Payment Confirmation operations. It changes no OpenAPI endpoint or schema.

Confirmed decisions:
- Regional Core Banking API generates and verifies OTP values.
- OTP plaintext is transient and must never be persisted or logged.
- OTP verification evidence uses HMAC-SHA-256 with an externally managed key.
- Three invalid attempts lock the challenge.
- Replacement creates a new challenge/OTP and invalidates the previous challenge.
- SMS and EMAIL are delivery capabilities behind an outbound port.
- No BKSMS SQL is implemented until La Regionale supplies authoritative physical mapping.
- No email transport is implemented until its Regional mechanism is approved.

The R5.1 in-memory repositories and no-op delivery adapter are test/reference adapters only.
They are not production persistence or delivery implementations. Durable persistence and physical
SMS/email integration belong to the next infrastructure increment.
"""
}

for name, body in files.items():
    p=Path(name); p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(textwrap.dedent(body).lstrip(),encoding="utf-8",newline="\n")
    print("WROTE",name)

readme=Path("README.md")
text=readme.read_text(encoding="utf-8")
marker="## R5.1 — Payment Confirmation / OTP"
if marker not in text:
    text += textwrap.dedent("""

    ## R5.1 — Payment Confirmation / OTP

    R5.1 introduces the provider-independent challenge/OTP domain, secure OTP verification,
    idempotency/recovery ports and lifecycle tests behind the six approved Regional operations.
    It does not implement BKSMS SQL or physical email transport; those require approved Regional
    infrastructure evidence. No OpenAPI generation is performed by this patch.

    See `documentation/architecture/R5_1_PAYMENT_CONFIRMATION_OTP_BASELINE.md`.
    """)
    readme.write_text(text,encoding="utf-8",newline="\n")
    print("MODIFIED README.md")

print("\nApplied R5.1 provider-independent baseline.")
print("No OpenAPI generation, commit, push, PR, branch creation or branch switch performed.")
print("\nRun:")
print("mvn -Pr3-no-openapi-generation -Dtest=PaymentConfirmationServiceTest,PaymentConfirmationContractCompatibilityCharacterizationTest test")
print("mvn -Pr3-no-openapi-generation verify")
