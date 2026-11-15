package com.regional.corebanking.payment.application.port.out;

import java.util.Optional;

/** Technical mapping only; never confirms banking execution. */
public interface PaymentBankEventTrackingPort {
    record TrackedEvent(String paymentReference, String operationCode, String eventNumber) {}
    Optional<TrackedEvent> findByPaymentReference(String institution, String reference);
    Optional<TrackedEvent> findByIdempotencyKey(String institution, String key);
}
