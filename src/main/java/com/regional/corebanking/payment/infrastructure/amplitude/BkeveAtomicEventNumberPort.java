package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;

/**
 * Banking integration boundary: implementations must obtain a unique EVE using
 * a bank-approved atomic operation, including multi-instance coordination.
 * A MAX(eve) followed by an existence check does not satisfy this contract.
 *
 * The bank must approve the uniqueness scope, reservation/commit semantics,
 * retry and recovery before a production implementation is supplied.
 */
interface BkeveAtomicEventNumberPort {
    String allocateAtomically(String operationCode, String eventTable,
                              LocalDate accountingDate);
}
