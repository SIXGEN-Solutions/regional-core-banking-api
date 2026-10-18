package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.domain.BkeveEvent;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.PaymentProviderEvent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BkeveFingerprintTest {
    private PaymentExecutionCommand command(Map<String, Object> fields) {
        var event = new PaymentProviderEvent("PAY-1", "001", "XAF", "PAYMENT", "SYSTEM",
                "00001-12345678901-42", "00001-12345678902-40",
                new BigDecimal("1000"), new BigDecimal("990"), "Payment",
                "00001", "00001", "CLIENT-A", new BkeveEvent(fields));
        return new PaymentExecutionCommand("PAY-1", "v1", event,
                OffsetDateTime.parse("2026-10-18T12:00:00Z"));
    }

    @Test void everySuppliedBankFieldParticipatesInIdempotencyFingerprint() {
        String original = PaymentExecutionFingerprint.sha256(command(Map.of("NOM1", "NAME-A")));
        String changed = PaymentExecutionFingerprint.sha256(command(Map.of("NOM1", "NAME-B")));
        assertNotEquals(original, changed);
        assertEquals(original, PaymentExecutionFingerprint.sha256(command(Map.of("NOM1", "NAME-A"))));
    }
}
