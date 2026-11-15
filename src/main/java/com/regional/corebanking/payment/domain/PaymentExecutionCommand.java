package com.regional.corebanking.payment.domain;

import java.time.OffsetDateTime;

public record PaymentExecutionCommand(
        String paymentReference,
        String snapshotVersion,
        PaymentProviderEvent providerEvent,
        OffsetDateTime requestedAt) {
}
