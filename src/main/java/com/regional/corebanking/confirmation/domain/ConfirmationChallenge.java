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
        Instant verifiedAt, Instant replacedAt, Instant revokedAt,
        DeliveryStatus deliveryStatus, Instant deliveryRequestedAt, Instant sentAt) {
    public ConfirmationChallenge {
        deliveryChannels = deliveryChannels == null ? Set.of() : Set.copyOf(deliveryChannels);
        deliveryStatus = deliveryStatus == null ? DeliveryStatus.CREATED : deliveryStatus;
    }
    public ConfirmationChallenge(String challengeReference, String paymentReference, String customerReference,
            String debtorAccountReference, BigDecimal amount, String currency, String otpVerifier, String otpKeyVersion,
            ChallengeStatus status, ConfirmationBusinessCode businessCode, int failedAttempts, int replacementCount,
            Set<DeliveryChannel> deliveryChannels, Instant createdAt, Instant expiresAt,
            Instant verifiedAt, Instant replacedAt, Instant revokedAt) {
        this(challengeReference,paymentReference,customerReference,debtorAccountReference,amount,currency,otpVerifier,
                otpKeyVersion,status,businessCode,failedAttempts,replacementCount,deliveryChannels,createdAt,expiresAt,
                verifiedAt,replacedAt,revokedAt,DeliveryStatus.CREATED,null,null);
    }
    public ConfirmationChallenge deliveryRequested(Instant at) { return deliveryCopy(DeliveryStatus.REQUESTED,at,null,businessCode); }
    public ConfirmationChallenge deliveryAccepted(Instant at) { return deliveryCopy(DeliveryStatus.ACCEPTED,deliveryRequestedAt,at,businessCode); }
    public ConfirmationChallenge deliveryFailed() { return deliveryCopy(DeliveryStatus.FAILED,deliveryRequestedAt,null,ConfirmationBusinessCode.DELIVERY_FAILED); }
    public ConfirmationChallenge deliveryUnknown() { return deliveryCopy(DeliveryStatus.UNKNOWN,deliveryRequestedAt,null,ConfirmationBusinessCode.DEPENDENCY_RESULT_UNKNOWN); }
    private ConfirmationChallenge deliveryCopy(DeliveryStatus d, Instant requested, Instant sent, ConfirmationBusinessCode code) {
        return new ConfirmationChallenge(challengeReference,paymentReference,customerReference,debtorAccountReference,amount,currency,
                otpVerifier,otpKeyVersion,status,code,failedAttempts,replacementCount,deliveryChannels,createdAt,expiresAt,
                verifiedAt,replacedAt,revokedAt,d,requested,sent);
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
                replacements,deliveryChannels,createdAt,expiresAt,verified,replaced,revoked,
                deliveryStatus,deliveryRequestedAt,sentAt);
    }
}
