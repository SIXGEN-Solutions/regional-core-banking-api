package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BkeveJdbcParameterMapperTest {
    private static final String CSV = "documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv";
    private static final java.util.Set<String> CLIENT = java.util.Set.of(
            "AGSA", "AGE", "OPE", "MHT1", "MON1", "MHT2", "MON2", "LIB1", "CSP1");

    @Test
    void columnsAre233UniqueAndMatchLegacyMatrixExactly() throws Exception {
        var lines = Files.readAllLines(Path.of(CSV));
        assertEquals(234, lines.size());
        assertEquals(233, BkeveJdbcColumnOrder.COLUMNS.size());
        assertEquals(233, new HashSet<>(BkeveJdbcColumnOrder.COLUMNS).size());
        for (int index = 0; index < 233; index++) {
            // CSV: position and column are the first two fields, neither quoted.
            String[] cells = lines.get(index + 1).split(",", 3);
            assertEquals(Integer.toString(index + 1), cells[0]);
            assertEquals(cells[1], BkeveJdbcColumnOrder.COLUMNS.get(index));
        }
    }

    @Test
    void mapsApprovedClientAmountsToBothLegsAndBindsEveryJdbcPosition() throws Exception {
        var bank = bankValues();
        var mapper = new BkeveJdbcParameterMapper();
        var params = mapper.map(event(), bank);
        assertEquals(new BigDecimal("1500.25"), at(params, "MON1"));
        assertEquals(new BigDecimal("1500.25"), at(params, "MHT1"));
        assertEquals(new BigDecimal("1495.25"), at(params, "MON2"));
        assertEquals(new BigDecimal("1495.25"), at(params, "MHT2"));
        assertEquals("00001", at(params, "AGSA"));
        assertEquals("VSixpay", at(params, "CSP1"));
        assertEquals("BANK-EVE", at(params, "EVE"));
        Map<Integer, Object> bound = new HashMap<>();
        AtomicInteger calls = new AtomicInteger();
        var statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set") && args != null && args.length >= 2) {
                        bound.put((Integer) args[0], args[1]);
                        calls.incrementAndGet();
                    }
                    return null;
                });
        mapper.bind(statement, params);
        assertEquals(233, calls.get());
        for (int i = 1; i <= 233; i++) assertEquals(params.valueAt(i), bound.get(i));
    }

    @Test
    void failsClosedForMissingBankEvidenceAndRejectsOverrides() {
        var mapper = new BkeveJdbcParameterMapper();
        var bank = bankValues();
        bank.remove("EVE");
        assertThrows(IllegalStateException.class, () -> mapper.map(event(), bank));
        bank.put("EVE", "BANK-EVE");
        bank.put("MON1", BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(event(), bank));
    }

    private static Object at(BkeveJdbcParameterMapper.Parameters p, String column) {
        return p.valueAt(BkeveJdbcColumnOrder.COLUMNS.indexOf(column) + 1);
    }

    private static Map<String, Object> bankValues() {
        var map = new HashMap<String, Object>();
        // Test-only sentinels: NOT bank-approved defaults or example production BKEVE.
        for (String name : BkeveJdbcColumnOrder.COLUMNS) {
            if (!CLIENT.contains(name)) map.put(name, "TEST-UNVALIDATED");
        }
        map.put("EVE", "BANK-EVE");
        return map;
    }

    private static PaymentProviderEvent event() {
        return new PaymentProviderEvent(
                "PAY-1", "001", "XAF", "PAYMENT", "SYSTEM",
                "00001-12345678901-42", "00001-12345678902-40",
                new BigDecimal("1500.25"), new BigDecimal("1495.25"),
                "Payment", "00001", "00001", "VSixpay");
    }
}
