package com.regional.corebanking.payment.domain;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BkeveEventTest {
    @Test void exposesAll233ColumnsAndPreservesDistinctConsumerValues() {
        assertEquals(233, BkeveEvent.COLUMNS.size());
        var supplied = new HashMap<String, Object>();
        supplied.put("AGE1", "00001");
        supplied.put("DEV1", "001");
        supplied.put("NCP1", "12345678901");
        supplied.put("MHT1", new java.math.BigDecimal("1000.00"));
        supplied.put("MON1", new java.math.BigDecimal("990.00"));
        var event = new BkeveEvent(supplied);
        supplied.clear();
        assertEquals("00001", event.values().get("AGE1"));
        assertEquals(new java.math.BigDecimal("1000.00"), event.values().get("MHT1"));
        assertEquals(new java.math.BigDecimal("990.00"), event.values().get("MON1"));
        assertEquals(5, event.values().size());
    }

    @Test void rejectsClientControlOfRegionalFieldsAndUnknownColumns() {
        assertThrows(IllegalArgumentException.class, () -> new BkeveEvent(Map.of("EVE", "000001")));
        assertThrows(IllegalArgumentException.class, () -> new BkeveEvent(Map.of("DCO", "2026-10-18")));
        assertThrows(IllegalArgumentException.class, () -> new BkeveEvent(Map.of("UNKNOWN_COLUMN", "x")));
    }
}
