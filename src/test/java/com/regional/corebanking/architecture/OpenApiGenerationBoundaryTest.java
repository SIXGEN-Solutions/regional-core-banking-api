package com.regional.corebanking.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenApiGenerationBoundaryTest {

    @Test
    void r2MustNotContainGeneratedOpenApiSources() {
        assertFalse(
                Files.exists(Path.of("target/generated-sources/openapi")),
                "R2 must not execute OpenAPI generation"
        );
    }
}
