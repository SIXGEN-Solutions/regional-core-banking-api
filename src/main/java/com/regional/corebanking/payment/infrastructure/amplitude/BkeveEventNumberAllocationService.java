package com.regional.corebanking.payment.infrastructure.amplitude;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * R6.6.5 preparatory allocation boundary. This class does not invent an
 * Informix locking strategy or claim that a number is transactionally reserved.
 * Production readiness depends on a bank-approved atomic port implementation.
 */
final class BkeveEventNumberAllocationService {
    private static final Set<String> RECOGNIZED_TABLES = Set.of("BKEVE", "BKEVE_EOD");
    private static final Pattern SIX_DIGITS = Pattern.compile("[0-9]{6}");
    private final BkeveAtomicEventNumberPort bankPort;

    BkeveEventNumberAllocationService(BkeveAtomicEventNumberPort bankPort) {
        this.bankPort = Objects.requireNonNull(bankPort, "bankPort");
    }

    String allocate(String operationCode, String eventTable, LocalDate accountingDate) {
        if (operationCode == null || operationCode.isBlank()) {
            throw new IllegalArgumentException("operationCode is required");
        }
        if (!RECOGNIZED_TABLES.contains(eventTable)) {
            throw new IllegalArgumentException("Unsupported BKEVE event table");
        }
        Objects.requireNonNull(accountingDate, "accountingDate");
        String number = bankPort.allocateAtomically(operationCode, eventTable, accountingDate);
        if (number == null || !SIX_DIGITS.matcher(number).matches()) {
            throw new IllegalStateException("Bank allocation returned an invalid EVE");
        }
        return number;
    }
}
