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
        String csp1,
        BkeveEvent bkeveEvent) {
    public PaymentProviderEvent {
        if (bkeveEvent == null) bkeveEvent = BkeveEvent.empty();
    }

    /** Compatibility with existing consumer-side command constructors. */
    public PaymentProviderEvent(String paymentReference, String operationCode, String currency,
                                String nature, String technicalUser, String debtorAccountReference,
                                String creditorAccountReference, BigDecimal debitAmount,
                                BigDecimal creditAmount, String label, String agsa,
                                String age, String csp1) {
        this(paymentReference, operationCode, currency, nature, technicalUser,
             debtorAccountReference, creditorAccountReference, debitAmount,
             creditAmount, label, agsa, age, csp1, BkeveEvent.empty());
    }
}
