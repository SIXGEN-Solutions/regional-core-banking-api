package com.regional.corebanking.customer.infrastructure.amplitude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("customer-oracle-mock")
@EnabledIfEnvironmentVariable(named = "RUN_CUSTOMER_ORACLE_IT", matches = "true")
class CustomerOracleIT {

    private static final String CORRELATION_ID =
            "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerFlowReadsRealOracleMockData() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .queryParam("financialInstitutionCode", "REGIONAL")
                        .queryParam("niu", "NIU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.customers[0].customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.customers[0].niu").value("NIU-001"));

        mockMvc.perform(get("/api/v1/customers/CUS-001")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Financial-Institution-Code", "REGIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.niu").value("NIU-001"))
                .andExpect(jsonPath("$.legalName").value("Jane Doe"))
                .andExpect(jsonPath("$.phoneNumber").value("+237600000000"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));

        mockMvc.perform(get("/api/v1/customers")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .queryParam("financialInstitutionCode", "REGIONAL")
                        .queryParam("niu", "NIU-INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(0));
    }

    @Test
    void compositeVerificationStaysIndeterminateWithoutAuthoritativeKycVerificationEvidence()
            throws Exception {
        String body = "{\n"
                + "  \"accountReference\": \"001-123456-7\",\n"
                + "  \"expectedNiu\": \"NIU-001\",\n"
                + "  \"expectedAccountHolder\": \"Jane Doe\",\n"
                + "  \"requiredKycFields\": [\"niu\", \"legalName\", \"phoneNumber\", \"email\"],\n"
                + "  \"requestedAt\": \"2026-09-13T00:00:00Z\"\n"
                + "}";

        mockMvc.perform(post("/api/v1/customer-verifications")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.accountReference").value("001-123456-7"))
                .andExpect(jsonPath("$.outcome").value("INDETERMINATE"));
    }
}
