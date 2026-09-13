package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerApiMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerApiMapper mapper = new CustomerApiMapper(objectMapper);

    @Test
    void mapsCustomerSearchToRegionalTransport() throws Exception {
        var result = mapper.toSearchResult(List.of(new CustomerSummary(
                "CUS-001", "CUS-001", "REGIONAL", "NIU-001", "Jane Doe"
        )));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.path("resultCount").asInt()).isEqualTo(1);
        assertThat(json.path("customers").get(0).path("customerReference").asText())
                .isEqualTo("CUS-001");
        assertThat(json.path("customers").get(0).path("niu").asText())
                .isEqualTo("NIU-001");
    }

    @Test
    void mapsCustomerIdentityToRegionalTransport() throws Exception {
        var result = mapper.toIdentity(new CustomerIdentity(
                "CUS-001",
                "CUS-001",
                "REGIONAL",
                "NIU-001",
                "Jane Doe",
                "+237600000000",
                "jane@example.com",
                CustomerIdentity.KycStatus.UNKNOWN,
                List.of(),
                null,
                Instant.parse("2026-09-13T18:00:00Z")
        ));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.path("customerReference").asText()).isEqualTo("CUS-001");
        assertThat(json.path("source").asText()).isEqualTo("AMPLITUDE");
        assertThat(json.path("kycStatus").asText()).isEqualTo("UNKNOWN");
    }
}
