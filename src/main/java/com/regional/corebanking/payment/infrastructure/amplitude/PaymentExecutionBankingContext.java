package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;

/**
 * Authoritative Amplitude context used by the BKEVE write.
 */
record PaymentExecutionBankingContext(String eventNumber, LocalDate accountingDate, boolean nightMode,
                                      String eventTable, LocalDate debitValueDate, LocalDate creditValueDate,
                                      String currency) {
}
