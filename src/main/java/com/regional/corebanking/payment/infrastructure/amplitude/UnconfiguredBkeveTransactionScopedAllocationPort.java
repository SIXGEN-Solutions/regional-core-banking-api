package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import java.time.LocalDate;

/** No financial execution until a bank-approved multi-instance port is provided. */
final class UnconfiguredBkeveTransactionScopedAllocationPort
        implements BkeveTransactionScopedAllocationPort {
    @Override
    public <T> T allocateAndWrite(String operationCode, String eventTable,
                                   LocalDate accountingDate, FinancialWrite<T> write) {
        throw new PaymentExecutionUnavailableException(
                "Bank-atomic EVE allocation and BKEVE commit are not approved/configured");
    }
}
