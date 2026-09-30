package com.regional.corebanking.payment.infrastructure.amplitude;
import java.time.LocalDate;
/** R6.5 authoritative Amplitude context required by the future BKEVE write. */
record PaymentExecutionBankingContext(String eventNumber, LocalDate accountingDate, boolean nightMode, String eventTable, LocalDate debitValueDate, LocalDate creditValueDate, String currency) {}
