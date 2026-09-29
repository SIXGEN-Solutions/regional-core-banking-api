package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UnconfiguredPaymentExecutionBankingAdapterTest {

    private final UnconfiguredPaymentExecutionBankingAdapter adapter =
            new UnconfiguredPaymentExecutionBankingAdapter();

    @Test
    void failsClosedInsteadOfInventingOrReplayingFinancialExecution() {
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> adapter.findByIdempotencyKey("REGIONAL", "idem-123456789012"));
    }
}
