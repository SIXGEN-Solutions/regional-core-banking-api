package com.regional.corebanking.payment.domain;

import java.math.BigDecimal;

public record PaymentProviderEntry(
        int sequence,
        String direction,
        String accountReference,
        BigDecimal amount,
        String currency,
        String paymentReference) {
}
