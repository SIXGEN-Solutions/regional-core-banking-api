package com.regional.corebanking.confirmation.application.service;

import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.exception.ConfirmationRejectedException;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import com.regional.corebanking.confirmation.application.port.out.OtpSecurityPort;
import com.regional.corebanking.confirmation.domain.ChallengeStatus;
import com.regional.corebanking.confirmation.domain.ConfirmationBusinessCode;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.ConfirmationCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class PaymentConfirmationService implements PaymentConfirmationUseCase {
    private final ChallengeRepository challenges;
    private final IdempotencyRepository idempotency;
    private final OtpSecurityPort otp;
    private final ConfirmationDeliveryPort delivery;
    private final Clock clock;
    private final Duration ttl;
    private final int maxAttempts;
    private final int maxReplacements;

    public PaymentConfirmationService(
            ChallengeRepository challenges,
            IdempotencyRepository idempotency,
            OtpSecurityPort otp,
            ConfirmationDeliveryPort delivery,
            Clock clock,
            Duration ttl,
            int maxAttempts,
            int maxReplacements
    ) {
        this.challenges = challenges;
        this.idempotency = idempotency;
        this.otp = otp;
        this.delivery = delivery;
        this.clock = clock;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.maxReplacements = maxReplacements;
    }

    @Override
    public ConfirmationChallenge create(String institution, String key, ConfirmationCommand.Create command) {
        String fingerprint = fingerprint(
                "CREATE", institution, command.paymentReference(), command.customerReference(),
                command.debtorAccountReference(), command.amount().toPlainString(), command.currency());

        return idempotency.execute(key, "CREATE", fingerprint, () -> {
            Instant now = clock.instant();
            String reference = "CHL-" + UUID.randomUUID();
            char[] value = otp.generate();
            try {
                ConfirmationChallenge challenge = new ConfirmationChallenge(
                        reference, command.paymentReference(), command.customerReference(),
                        command.debtorAccountReference(), command.amount(), command.currency(),
                        otp.verifier(reference, value), otp.keyVersion(),
                        ChallengeStatus.ACTIVE, ConfirmationBusinessCode.CHALLENGE_ACTIVE,
                        0, 0, delivery.enabledChannels(), now, now.plus(ttl),
                        null, null, null);
                challenges.save(challenge);
                return applyDeliveryOutcome(challenge, delivery.dispatch(challenge, value));
            } finally {
                Arrays.fill(value, '\0');
            }
        });
    }

    @Override
    public ConfirmationChallenge verify(String institution, String key, String reference,
                                        ConfirmationCommand.Verify command) {
        String fingerprint = fingerprint(
                "VERIFY", institution, reference, command.paymentReference(),
                otp.verifier("IDEMPOTENCY:" + reference, command.otp()));
        try {
            return idempotency.execute(key, "VERIFY", fingerprint, () ->
                    challenges.update(reference, current -> {
                        requirePayment(current, command.paymentReference());
                        current = expire(current);
                        if (current.status() != ChallengeStatus.ACTIVE) {
                            return current;
                        }
                        if (otp.matches(reference, command.otp(), current.otpVerifier())) {
                            return current.verified(clock.instant());
                        }
                        int attempts = current.failedAttempts() + 1;
                        return attempts >= maxAttempts
                                ? current.failed(attempts, ChallengeStatus.LOCKED,
                                ConfirmationBusinessCode.CHALLENGE_LOCKED)
                                : current.failed(attempts, ChallengeStatus.ACTIVE,
                                ConfirmationBusinessCode.OTP_INVALID);
                    }));
        } finally {
            Arrays.fill(command.otp(), '\0');
        }
    }

    @Override
    public ConfirmationChallenge replace(String institution, String key, String reference,
                                         ConfirmationCommand.Replace command) {
        String fingerprint = fingerprint("REPLACE", institution, reference, command.paymentReference());

        return idempotency.execute(key, "REPLACE", fingerprint, () -> {
            ConfirmationChallenge previous = challenges.update(reference, current -> {
                requirePayment(current, command.paymentReference());
                current = expire(current);
                if (current.status() != ChallengeStatus.ACTIVE) {
                    throw new ConfirmationRejectedException("Only an ACTIVE challenge can be replaced");
                }
                if (current.replacementCount() >= maxReplacements) {
                    return current.failed(current.failedAttempts(), current.status(),
                            ConfirmationBusinessCode.RESEND_NOT_ALLOWED);
                }
                return current.replaced(clock.instant());
            });

            if (previous.businessCode() == ConfirmationBusinessCode.RESEND_NOT_ALLOWED) {
                return previous;
            }

            char[] value = otp.generate();
            try {
                Instant now = clock.instant();
                String newReference = "CHL-" + UUID.randomUUID();
                ConfirmationChallenge replacement = new ConfirmationChallenge(
                        newReference, previous.paymentReference(), previous.customerReference(),
                        previous.debtorAccountReference(), previous.amount(), previous.currency(),
                        otp.verifier(newReference, value), otp.keyVersion(),
                        ChallengeStatus.ACTIVE, ConfirmationBusinessCode.CHALLENGE_ACTIVE,
                        0, previous.replacementCount() + 1, delivery.enabledChannels(),
                        now, now.plus(ttl), null, null, null);
                challenges.save(replacement);
                return applyDeliveryOutcome(replacement, delivery.dispatch(replacement, value));
            } finally {
                Arrays.fill(value, '\0');
            }
        });
    }

    @Override
    public ConfirmationChallenge get(String institution, String reference) {
        return challenges.update(reference, this::expire);
    }

    @Override
    public ConfirmationChallenge recover(String institution, String key) {
        return idempotency.find(key)
                .map(IdempotencyRepository.Entry::result)
                .orElseThrow(() -> new ChallengeNotFoundException("idempotency:" + key));
    }

    @Override
    public ConfirmationChallenge revoke(String institution, String key, String reference,
                                        ConfirmationCommand.Revoke command) {
        String fingerprint = fingerprint(
                "REVOKE", institution, reference, command.paymentReference(), command.reasonCode());

        return idempotency.execute(key, "REVOKE", fingerprint, () ->
                challenges.update(reference, current -> {
                    requirePayment(current, command.paymentReference());
                    current = expire(current);
                    if (current.status() == ChallengeStatus.REVOKED) {
                        return current;
                    }
                    if (current.status() != ChallengeStatus.ACTIVE) {
                        throw new ConfirmationRejectedException("Only an ACTIVE challenge can be revoked");
                    }
                    return current.revoked(clock.instant());
                }));
    }

    private ConfirmationChallenge applyDeliveryOutcome(
            ConfirmationChallenge challenge,
            ConfirmationDeliveryPort.Outcome outcome
    ) {
        if (outcome == ConfirmationDeliveryPort.Outcome.DELIVERED) {
            return challenge;
        }
        ConfirmationBusinessCode code = outcome == ConfirmationDeliveryPort.Outcome.CONFIRMED_FAILURE
                ? ConfirmationBusinessCode.DELIVERY_FAILED
                : ConfirmationBusinessCode.DEPENDENCY_RESULT_UNKNOWN;

        return challenges.update(
                challenge.challengeReference(),
                current -> current.failed(current.failedAttempts(), current.status(), code));
    }

    private ConfirmationChallenge expire(ConfirmationChallenge challenge) {
        return challenge.status() == ChallengeStatus.ACTIVE
                && !clock.instant().isBefore(challenge.expiresAt())
                ? challenge.expired()
                : challenge;
    }

    private static void requirePayment(ConfirmationChallenge challenge, String paymentReference) {
        if (!Objects.equals(challenge.paymentReference(), paymentReference)) {
            throw new ConfirmationRejectedException("Challenge is not bound to this payment");
        }
    }

    private static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
                digest.update(bytes);
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
