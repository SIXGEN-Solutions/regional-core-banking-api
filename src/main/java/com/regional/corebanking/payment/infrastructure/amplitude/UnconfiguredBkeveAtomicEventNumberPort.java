package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import java.time.LocalDate;

/** Fail closed until La Regionale approves the physical atomic allocation. */
final class UnconfiguredBkeveAtomicEventNumberPort implements BkeveAtomicEventNumberPort {
    @Override
    public String allocateAtomically(String operationCode, String eventTable,
                                     LocalDate accountingDate) {
        throw new PaymentExecutionUnavailableException(
                "Atomic BKEVE event-number allocation is not bank-approved or configured");
    }
}
