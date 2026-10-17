package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import com.regional.corebanking.payment.domain.PaymentExecutionOutcome;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentExecutionRecoveryCoordinatorTest {
    private static PaymentExecutionResult result(String ref, PaymentExecutionOutcome state, String bankRef) {
        return new PaymentExecutionResult(ref, state, List.of(), bankRef, null,
                OffsetDateTime.parse("2026-10-17T12:00:00Z"));
    }

    @Test void localUnknownRequiresReadOnlyAuthoritativeLookupWithNoReposting() {
        var local = mock(PaymentExecutionPersistencePort.class);
        var bank = mock(PaymentExecutionBankingPort.class);
        when(local.findByPaymentReference("REGIONAL", "P1"))
                .thenReturn(Optional.of(result("P1", PaymentExecutionOutcome.UNKNOWN, null)));
        when(bank.findByPaymentReference("REGIONAL", "P1"))
                .thenReturn(Optional.of(result("P1", PaymentExecutionOutcome.COMPLETED, "BANK1")));
        var recovery = new PaymentExecutionRecoveryCoordinator(local, bank);
        assertEquals("BANK1", recovery.byPaymentReference("REGIONAL", "P1").bankReference());
        verify(bank, never()).execute(anyString(), anyString(), any());
        verify(bank).findByPaymentReference("REGIONAL", "P1");
        verifyNoMoreInteractions(bank);
    }

    @Test void bankAbsenceNeverMeansNotPostedAndNeverReplays() {
        var local = mock(PaymentExecutionPersistencePort.class);
        var bank = mock(PaymentExecutionBankingPort.class);
        when(local.findByIdempotencyKey("REGIONAL", "K1"))
                .thenReturn(Optional.of(result("P1", PaymentExecutionOutcome.UNKNOWN, null)));
        var recovery = new PaymentExecutionRecoveryCoordinator(local, bank);
        assertEquals(PaymentExecutionOutcome.UNKNOWN,
                recovery.byIdempotencyKey("REGIONAL", "K1").outcome());
        verify(bank, never()).execute(anyString(), anyString(), any());
    }

    @Test void mismatchedReferenceFailsClosed() {
        var local = mock(PaymentExecutionPersistencePort.class);
        var bank = mock(PaymentExecutionBankingPort.class);
        when(bank.findByPaymentReference("REGIONAL", "P1"))
                .thenReturn(Optional.of(result("OTHER", PaymentExecutionOutcome.COMPLETED, "BANK1")));
        var recovery = new PaymentExecutionRecoveryCoordinator(local, bank);
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> recovery.byPaymentReference("REGIONAL", "P1"));
        verify(bank, never()).execute(anyString(), anyString(), any());
    }

    @Test void incompleteCompletedEvidenceIsRefused() {
        var local = mock(PaymentExecutionPersistencePort.class);
        var bank = mock(PaymentExecutionBankingPort.class);
        when(bank.findByIdempotencyKey("REGIONAL", "K1"))
                .thenReturn(Optional.of(result("P1", PaymentExecutionOutcome.COMPLETED, null)));
        var recovery = new PaymentExecutionRecoveryCoordinator(local, bank);
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> recovery.byIdempotencyKey("REGIONAL", "K1"));
    }

    @Test void missingResultIsNotFoundWithoutExecution() {
        var local = mock(PaymentExecutionPersistencePort.class);
        var bank = mock(PaymentExecutionBankingPort.class);
        var recovery = new PaymentExecutionRecoveryCoordinator(local, bank);
        assertThrows(PaymentExecutionNotFoundException.class,
                () -> recovery.byIdempotencyKey("REGIONAL", "missing"));
        verify(bank, never()).execute(anyString(), anyString(), any());
    }
}
