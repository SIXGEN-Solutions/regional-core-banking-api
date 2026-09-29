package com.regional.corebanking.payment.domain;

public record PaymentExecutionCheck(
        PaymentExecutionCheckType type,
        PaymentExecutionCheckResult result,
        String reasonCode) {
}
