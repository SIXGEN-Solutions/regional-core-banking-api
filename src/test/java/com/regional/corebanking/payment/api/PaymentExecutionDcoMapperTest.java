package com.regional.corebanking.payment.api;

import com.regional.corebanking.payment.domain.PaymentExecutionCheck;
import com.regional.corebanking.payment.domain.PaymentExecutionOutcome;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
import com.regional.corebanking.generated.model.PaymentEventResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutionDcoMapperTest {
    private final PaymentExecutionApiMapper mapper = new PaymentExecutionApiMapper();

    @Test
    void completedResponseExposesBankAccountingDate() {
        var dco = LocalDate.of(2026, 10, 19);
        var result = new PaymentExecutionResult("PAY-1", PaymentExecutionOutcome.COMPLETED,
                List.<PaymentExecutionCheck>of(), "787/000002", null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"), dco);
        assertEquals(dco, mapper.toApi(result).getDco());
    }

    @Test
    void rejectedResponseKeepsExplicitNullableDco() {
        var result = new PaymentExecutionResult("PAY-REJECT", PaymentExecutionOutcome.REJECTED,
                List.<PaymentExecutionCheck>of(), null, "ACCOUNT_INVALID",
                OffsetDateTime.parse("2026-11-06T02:36:13Z"));
        assertNull(mapper.toApi(result).getDco());
    }

    @Test
    void recoveredCompletedWithNoHistoricalChecksDoesNotInventThem() {
        var recovered = new PaymentExecutionResult("PAY-RECOVERED", PaymentExecutionOutcome.COMPLETED,
                List.<PaymentExecutionCheck>of(), "OPE/000123", null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"), LocalDate.of(2026, 11, 6));
        var api = mapper.toApi(recovered);
        assertEquals(PaymentEventResult.OutcomeEnum.COMPLETED, api.getOutcome());
        assertTrue(api.getChecks().isEmpty());
    }

    @Test
    void persistedChecksRemainUnchangedAfterMapping() {
        var checks = List.of(
                new PaymentExecutionCheck(
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckType.ACCOUNT_VALID,
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckResult.PASS, null),
                new PaymentExecutionCheck(
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckType.DEBIT_ALLOWED,
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckResult.PASS, null),
                new PaymentExecutionCheck(
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT,
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckResult.PASS, null));
        var original = new PaymentExecutionResult("PAY-ORIGINAL", PaymentExecutionOutcome.COMPLETED,
                checks, "OPE/000124", null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"), LocalDate.of(2026, 11, 6));
        var api = mapper.toApi(original);
        assertEquals(3, api.getChecks().size());
        assertTrue(api.getChecks().stream().allMatch(c -> c.getResult().name().equals("PASS")));
    }

    @Test
    void completedWithoutBankReferenceIsNeverReturnedAsSuccess() {
        var invalid = new PaymentExecutionResult("PAY-NO-BANK-REF", PaymentExecutionOutcome.COMPLETED,
                List.<PaymentExecutionCheck>of(), null, null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"), LocalDate.of(2026, 11, 6));
        assertThrows(com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException.class,
                () -> mapper.toApi(invalid));
    }

    @Test
    void incompleteHistoricalChecksAreNeverCompletedArtificially() {
        var original = new PaymentExecutionResult("PAY-INCOMPLETE", PaymentExecutionOutcome.UNKNOWN,
                List.of(new PaymentExecutionCheck(
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckType.ACCOUNT_VALID,
                        com.regional.corebanking.payment.domain.PaymentExecutionCheckResult.PASS, null)),
                null, null, OffsetDateTime.parse("2026-11-06T02:36:13Z"));
        assertThrows(com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException.class,
                () -> mapper.toApi(original));
    }

    @Test
    void completedWithoutDcoIsNeverReturnedAsSuccess() {
        var oldResult = new PaymentExecutionResult("PAY-1", PaymentExecutionOutcome.COMPLETED,
                List.<PaymentExecutionCheck>of(), "787/000002", null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"));
        assertThrows(com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException.class,
                () -> mapper.toApi(oldResult));
    }
}
