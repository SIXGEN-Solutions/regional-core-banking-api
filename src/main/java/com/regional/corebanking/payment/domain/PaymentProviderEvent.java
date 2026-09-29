package com.regional.corebanking.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentProviderEvent(
        String paymentReference,
        String operationCode,
        long eventNumber,
        String currency,
        String nature,
        LocalDate accountingDate,
        String technicalUser,
        String debtorAccountReference,
        String creditorAccountReference,
        BigDecimal amount,
        String label,
        boolean nightMode) {
}
