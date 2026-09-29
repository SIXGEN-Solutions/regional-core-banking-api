package com.regional.corebanking.payment.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentExecutionCommand(
        String paymentReference,
        String snapshotVersion,
        PaymentProviderEvent providerEvent,
        List<PaymentProviderEntry> providerEntries,
        OffsetDateTime requestedAt) {

    public PaymentExecutionCommand {
        providerEntries = List.copyOf(providerEntries);
    }
}
