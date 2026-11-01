
package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerApiMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final CustomerApiMapper mapper =
            new CustomerApiMapper(objectMapper);

    @Test
    void mapsBankAccountAndPreservesLeadingZeros() throws Exception {

        var account = new BankAccount(
                "00001-04942231051-47",
                "0494223",
                "REGIONAL",
                "************1-47",
                "001",
                BankAccount.AccountType.UNKNOWN,
                BankAccount.AccountStatus.ACTIVE,
                Set.of(),
                Instant.parse("2026-11-01T12:00:00Z"),
                "04942231051",
                "00001",
                "001",
                "",
                "47",
                "0494223",
                "YOUVOU NGUELEU JANE",
                "N",
                "N",
                LocalDate.of(2020, 1, 2),
                new BigDecimal("50000.00"),
                "",
                new BigDecimal("10000.00"),
                ""
        );

        // 1. Verify the domain model.
        assertThat(account.dou())
                .isEqualTo(LocalDate.of(2020, 1, 2));

        // 2. Verify mapping to the generated OpenAPI DTO.
        var response = mapper.toAccountSearchResult(List.of(account));

        assertThat(response.getAccounts()).hasSize(1);
        assertThat(response.getResultCount()).isEqualTo(1);

        var dto = response.getAccounts().getFirst();

        assertThat(dto.getDou())
                .isEqualTo(LocalDate.of(2020, 1, 2));

        assertThat(dto.getAge()).isEqualTo("00001");
        assertThat(dto.getNcp()).isEqualTo("04942231051");
        assertThat(dto.getDev()).isEqualTo("001");

        // 3. Verify the serialized JSON response.
        var json = objectMapper.readTree(
                objectMapper.writeValueAsString(response)
        );

        var item = json.path("accounts").get(0);

        assertThat(item).isNotNull();
        assertThat(item.has("dou")).isTrue();
        assertThat(item.path("dou").isTextual()).isTrue();

        assertThat(item.path("dou").asText())
                .isEqualTo("2020-01-02");

        // 4. Verify account identifiers and leading zeros.
        assertThat(item.path("age").asText())
                .isEqualTo("00001");

        assertThat(item.path("ncp").asText())
                .isEqualTo("04942231051");

        assertThat(item.path("dev").asText())
                .isEqualTo("001");

        assertThat(item.path("clc").asText())
                .isEqualTo("47");

        assertThat(item.path("cli").asText())
                .isEqualTo("0494223");

        // 5. Verify account status and balances.
        assertThat(item.path("status").asText())
                .isEqualTo("ACTIVE");

        assertThat(item.path("sin").decimalValue())
                .isEqualByComparingTo("50000.00");

        assertThat(item.path("sde").decimalValue())
                .isEqualByComparingTo("10000.00");

        assertThat(json.path("resultCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void mapsCustomerSearchToRegionalTransport() throws Exception {

        var result = mapper.toSearchResult(
                List.of(
                        new CustomerSummary(
                                "CUS-001",
                                "CUS-001",
                                "REGIONAL",
                                "NIU-001",
                                "Jane Doe"
                        )
                )
        );

        var json = objectMapper.readTree(
                objectMapper.writeValueAsString(result)
        );

        assertThat(json.path("resultCount").asInt())
                .isEqualTo(1);

        assertThat(json.path("customers").get(0)
                .path("customerReference").asText())
                .isEqualTo("CUS-001");

        assertThat(json.path("customers").get(0)
                .path("niu").asText())
                .isEqualTo("NIU-001");
    }

    @Test
    void mapsCustomerIdentityToRegionalTransport() throws Exception {

        var result = mapper.toIdentity(
                new CustomerIdentity(
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
                )
        );

        var json = objectMapper.readTree(
                objectMapper.writeValueAsString(result)
        );

        assertThat(json.path("customerReference").asText())
                .isEqualTo("CUS-001");

        assertThat(json.path("source").asText())
                .isEqualTo("AMPLITUDE");

        assertThat(json.path("kycStatus").asText())
                .isEqualTo("UNKNOWN");
    }
}
