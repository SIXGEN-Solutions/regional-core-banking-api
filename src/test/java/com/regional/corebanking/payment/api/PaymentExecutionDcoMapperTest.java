package com.regional.corebanking.payment.api;

import com.regional.corebanking.payment.domain.PaymentExecutionCheck;
import com.regional.corebanking.payment.domain.PaymentExecutionOutcome;
import com.regional.corebanking.payment.domain.PaymentExecutionResult;
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
    void completedWithoutDcoIsNeverReturnedAsSuccess() {
        var oldResult = new PaymentExecutionResult("PAY-1", PaymentExecutionOutcome.COMPLETED,
                List.<PaymentExecutionCheck>of(), "787/000002", null,
                OffsetDateTime.parse("2026-11-06T02:36:13Z"));
        assertThrows(com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException.class,
                () -> mapper.toApi(oldResult));
    }
}
