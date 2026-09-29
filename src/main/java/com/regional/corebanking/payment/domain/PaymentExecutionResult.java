package com.regional.corebanking.payment.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentExecutionResult(
        String paymentReference,
        PaymentExecutionOutcome outcome,
        List<PaymentExecutionCheck> checks,
        String bankReference,
        String reasonCode,
        OffsetDateTime observedAt) {

    public PaymentExecutionResult {
        checks = List.copyOf(checks);
    }
}
