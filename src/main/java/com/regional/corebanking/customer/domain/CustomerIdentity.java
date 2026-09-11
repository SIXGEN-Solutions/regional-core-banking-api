package com.regional.corebanking.customer.domain;

import java.time.Instant;
import java.util.List;

public record CustomerIdentity(
        String customerReference,
        String customerNumber,
        String financialInstitutionCode,
        String niu,
        String legalName,
        String phoneNumber,
        String email,
        KycStatus kycStatus,
        List<KycField> kycFields,
        Instant kycLastUpdatedAt,
        Instant retrievedAt
) {
    public enum KycStatus {
        COMPLETE, INCOMPLETE, EXPIRED, NOT_VERIFIED, UNKNOWN
    }

    public record KycField(
            String code,
            Object value,
            boolean present,
            boolean verified,
            Instant verifiedAt
    ) {
    }
}
