package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BkeveBankEnrichmentServiceTest {
    private final BkeveBankEnrichmentService service = new BkeveBankEnrichmentService();

    private PaymentExecutionBankingContext context() {
        return new PaymentExecutionBankingContext(
                "000123", LocalDate.of(2026, 10, 15), false, "BKEVE",
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16), "XAF");
    }

    @Test
    void mapsOnlySupportedR65FieldsWithoutInventingBankDefaults() {
        Map<String, Object> source = new HashMap<>();
        source.put("CLI1", "CLIENT-FROM-BANK");
        source.put("SOL1", null); // Explicit null, not a fabricated value.
        var values = service.enrich(context(), source);
        assertEquals("000123", values.get("EVE"));
        assertEquals(LocalDate.of(2026, 10, 15), values.get("DCO"));
        assertEquals(LocalDate.of(2026, 10, 14), values.get("DVA1"));
        assertEquals(LocalDate.of(2026, 10, 16), values.get("DVA2"));
        assertEquals("CLIENT-FROM-BANK", values.get("CLI1"));
        assertTrue(values.containsKey("SOL1"));
        assertNull(values.get("SOL1"));
        assertFalse(values.containsKey("DEV1"));
        assertFalse(values.containsKey("DEV2"));
        assertFalse(values.containsKey("ETA"));
        assertFalse(values.containsKey("DOU"));
        assertFalse(values.containsKey("UTI"));
        assertFalse(values.containsKey("AGE1"));
        assertFalse(values.containsKey("AGE2"));
        assertThrows(UnsupportedOperationException.class, () -> values.put("EVE", "changed"));
        assertEquals(2, source.size()); // No caller-map mutation.
    }

    @Test
    void refusesOverrideOfAuthoritativeOrCallerColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(context(), Map.of("EVE", "999999")));
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(context(), Map.of("DCO", LocalDate.now())));
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(context(), Map.of("MON1", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(context(), Map.of("NOT_A_BKEVE_COLUMN", "x")));
    }

    @Test
    void failsClosedOnIncompleteR65Context() {
        var missingEvent = new PaymentExecutionBankingContext(
                null, LocalDate.of(2026, 10, 15), false, "BKEVE",
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16), "XAF");
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(missingEvent, Map.of()));
        var missingDate = new PaymentExecutionBankingContext(
                "000123", null, false, "BKEVE",
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16), "XAF");
        assertThrows(IllegalArgumentException.class,
                () -> service.enrich(missingDate, Map.of()));
    }

    @Test
    void resultCanBePassedToR663MapperOnlyWithOtherExplicitBankValues() {
        var event = new com.regional.corebanking.payment.domain.PaymentProviderEvent(
                "PAY-1", "001", "XAF", "PAYMENT", "SYSTEM",
                "00001-12345678901-42", "00001-12345678902-40",
                new java.math.BigDecimal("1500.25"), new java.math.BigDecimal("1495.25"),
                "Payment", "00001", "00001", "VSixpay");
        var partial = service.enrich(context(), Map.of("CLI1", "BANK-CLIENT"));
        var mapper = new BkeveJdbcParameterMapper();
        var parameters = mapper.map(event, partial);

        assertEquals(233, parameters.values().size());
        assertEquals(
                "BANK-CLIENT",
                parameters.valueAt(BkeveJdbcColumnOrder.COLUMNS.indexOf("CLI1") + 1)
        );
        assertNull(
                parameters.valueAt(BkeveJdbcColumnOrder.COLUMNS.indexOf("DEV1") + 1)
        );
    }
}
