package com.regional.corebanking.payment.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentExecutionResult(
        String paymentReference,
        PaymentExecutionOutcome outcome,
        List<PaymentExecutionCheck> checks,
        String bankReference,
        String reasonCode,
        OffsetDateTime observedAt,
        LocalDate dco) {

    public PaymentExecutionResult {
        checks = List.copyOf(checks);
    }

    /** Existing call sites and previously serialized results remain compatible. */
    public PaymentExecutionResult(String paymentReference,
            PaymentExecutionOutcome outcome,
            List<PaymentExecutionCheck> checks,
            String bankReference,
            String reasonCode,
            OffsetDateTime observedAt) {
        this(paymentReference, outcome, checks, bankReference, reasonCode, observedAt, null);
    }
}
