package com.regional.corebanking.payment.domain;

import java.math.BigDecimal;

public record PaymentProviderEvent(
        String paymentReference,
        String operationCode,
        String currency,
        String nature,
        String technicalUser,
        String debtorAccountReference,
        String creditorAccountReference,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String label,
        String agsa,
        String age,
        String csp1) {
}
