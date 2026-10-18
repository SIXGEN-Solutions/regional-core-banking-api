package com.regional.corebanking.payment.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import static org.mockito.Mockito.*;

class BkeveLegacyJdbcBindingTest {
    @Test void bindsLegacyStringDecimalDateIntegerAndTypedNull() throws Exception {
        PreparedStatement stmt = mock(PreparedStatement.class);
        var values = new ArrayList<Object>(Collections.nCopies(233, null));
        values.set(0, "00001"); // AGSA
        values.set(15, new BigDecimal("2300.1234")); // MHT1
        values.set(17, java.time.LocalDate.of(2026, 10, 18)); // DVA1
        values.set(83, 2); // NBLIG
        var params = new BkeveJdbcParameterMapper.Parameters(BkeveJdbcColumnOrder.COLUMNS, values);
        new BkeveJdbcParameterMapper().bind(stmt, params);
        verify(stmt).setString(1, "00001");
        verify(stmt).setBigDecimal(16, new BigDecimal("2300.1234"));
        verify(stmt).setDate(18, Date.valueOf("2026-10-18"));
        verify(stmt).setInt(84, 2);
        verify(stmt).setNull(4, Types.CHAR); // EVE
        verify(stmt).setNull(20, Types.DECIMAL); // SOL1
    }
}
