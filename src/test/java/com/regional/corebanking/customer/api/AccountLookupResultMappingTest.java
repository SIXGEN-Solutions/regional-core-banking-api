package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountLookupResultMappingTest {
    @Test
    void missingAccountIsBusinessResultWithNullData() {
        CustomerApiMapper mapper = new CustomerApiMapper(new ObjectMapper().findAndRegisterModules());
        var result = mapper.toAccountLookupResult(null);
        assertEquals("COMPTE_INEXISTANT", result.getCode().toString());
        assertEquals("COMPTE INEXISTANT", result.getMessage());
        assertNull(result.getData());
    }
}
