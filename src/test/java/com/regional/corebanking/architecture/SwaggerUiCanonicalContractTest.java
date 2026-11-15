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
        // The two tracked YAML files are byte-identical in Git. On Windows,
        // Git/editor line-ending conversion may create CRLF/LF differences
        // in a local working tree; these are not a contract divergence.
        // Only normalize CRLF to LF; all other differences still fail.
        assertThat(Files.readString(RUNTIME).replace("\r\n", "\n"))
                .as("Swagger UI must serve the same canonical OpenAPI content")
                .isEqualTo(Files.readString(CANONICAL).replace("\r\n", "\n"));

        String applicationYaml =
                Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("url: /openapi/regional-core-banking-api-v1.yaml")
                .contains("disable-swagger-default-url: true");
    }
}
