package com.regional.corebanking.customer.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AccountLookupRouteContractTest {

    private static final String ROUTE = "/api/v1/getaccountinfo/{branchid}/{accountnumber}";

    @Test
    void springControllerExposesOnlyTheApprovedVersionedRoute() throws NoSuchMethodException {
        GetMapping mapping = AccountLookupApiAdapter.class
                .getMethod("getAccountByBranchAndNumber",
                        java.util.UUID.class, String.class, String.class, String.class)
                .getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(ROUTE);
    }

    @Test
    void canonicalAndSwaggerContractsDeclareTheVersionedPath() throws IOException {
        String canonical = Files.readString(Path.of(
                "contracts/openapi/regional-core-banking-api-v1.yaml"));
        String swagger = Files.readString(Path.of(
                "src/main/resources/static/openapi/regional-core-banking-api-v1.yaml"));

        assertThat(canonical).contains("  " + ROUTE + ":");
        assertThat(canonical).doesNotContain("  /getaccountinfo/{branchid}/{accountnumber}:");
        assertThat(swagger.replace("\r\n", "\n"))
                .isEqualTo(canonical.replace("\r\n", "\n"));
    }
}
