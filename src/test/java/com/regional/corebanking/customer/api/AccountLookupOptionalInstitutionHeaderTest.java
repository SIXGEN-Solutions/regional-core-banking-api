package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.application.service.CustomerVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountLookupOptionalInstitutionHeaderTest {
    private static final String CORRELATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String URL = "/api/v1/getaccountinfo/001/123456";
    private CustomerBankingPort bankPort;
    private CustomerVerificationUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        bankPort = mock(CustomerBankingPort.class);
        useCase = new CustomerVerificationService(bankPort);
        var controller = new AccountLookupApiAdapter(
                useCase,
                new CustomerApiMapper(new ObjectMapper().findAndRegisterModules()),
                "REGIONAL");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CustomerApiExceptionHandler())
                .build();
    }

    @Test
    void missingHeaderUsesConfiguredInstitution() throws Exception {
        when(bankPort.findAccountByAgencyAndNumber("REGIONAL", "001", "123456"))
                .thenReturn(null);
        mvc.perform(get(URL).header("X-Correlation-ID", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMPTE_INEXISTANT"));
        verify(bankPort).findAccountByAgencyAndNumber("REGIONAL", "001", "123456");
    }

    @Test
    void suppliedHeaderTakesPrecedenceOverConfiguration() throws Exception {
        mvc.perform(get(URL)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Financial-Institution-Code", "ANOTHER_BANK"))
                .andExpect(status().isOk());
        verify(bankPort).findAccountByAgencyAndNumber("ANOTHER_BANK", "001", "123456");
    }

    @Test
    void suppliedBlankHeaderIsNotSilentlyReplaced() throws Exception {
        mvc.perform(get(URL)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Financial-Institution-Code", " "))
                .andExpect(status().isBadRequest());
    }
}
