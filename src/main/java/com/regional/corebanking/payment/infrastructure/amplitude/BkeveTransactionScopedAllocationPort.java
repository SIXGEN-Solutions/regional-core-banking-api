package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Bank-owned concurrency boundary, valid for any number of API instances.
 * A production implementation MUST keep allocation and the associated BKEVE
 * write in one bank-approved atomic scope. An in-JVM lock or MAX(eve) is not
 * a valid implementation. The bank must approve the physical mechanism.
 *
 * No implementation of this port is enabled by this lot.
 */
interface BkeveTransactionScopedAllocationPort {
    @FunctionalInterface
    interface FinancialWrite<T> {
        T execute(String allocatedEventNumber) throws Exception;
    }

    /**
     * Calls the financial write at most once, while the bank-level exclusion
     * is held; returns only after its outcome is conclusively known.
     * If outcome is uncertain, must throw and NEVER replay the callback.
     */
    <T> T allocateAndWrite(String operationCode, String eventTable,
                           LocalDate accountingDate, FinancialWrite<T> write) throws Exception;
}
