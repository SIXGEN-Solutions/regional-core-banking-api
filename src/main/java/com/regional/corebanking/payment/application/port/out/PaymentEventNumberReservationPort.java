package com.regional.corebanking.payment.application.port.out;

/** PostgreSQL technical reservation; NEVER evidence of a financial posting. */
@FunctionalInterface
public interface PaymentEventNumberReservationPort {
    /**
     * Atomically advance API_EODProcess.lasteve and associate this EVE with
     * an existing idempotency claim. Called while the JVM OPE lock is held,
     * before any bank INSERT. A successful call commits its own PG transaction.
     */
    String reserve(String institution, String key, String operation, String firstCandidate);
}
