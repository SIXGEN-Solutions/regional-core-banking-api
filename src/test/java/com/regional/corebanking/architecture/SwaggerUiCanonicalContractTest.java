package com.regional.corebanking.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerUiCanonicalContractTest {

    private static final Path CANONICAL =
            Path.of("contracts/openapi/regional-core-banking-api-v1.yaml");

    private static final Path RUNTIME =
            Path.of("src/main/resources/static/openapi/regional-core-banking-api-v1.yaml");

    @Test
    void swaggerUiUsesAnExactCopyOfTheCanonicalRegionalContract() throws IOException {
        assertThat(Files.readAllBytes(RUNTIME))
                .isEqualTo(Files.readAllBytes(CANONICAL));

        String applicationYaml =
                Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("url: /openapi/regional-core-banking-api-v1.yaml")
                .contains("disable-swagger-default-url: true");
    }
}
