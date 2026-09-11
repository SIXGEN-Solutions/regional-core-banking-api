package com.regional.corebanking.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiGeneratedSourcesLocationTest {

    private static final Path EXPECTED_GENERATED_ROOT =
            Path.of("target/generated-sources/openapi").normalize();

    @Test
    void generatedOpenApiSourcesMustRemainUnderTarget() {
        Path normalized = EXPECTED_GENERATED_ROOT.toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();

        assertTrue(
                normalized.startsWith(target),
                "Generated OpenAPI sources must stay under target/"
        );
    }

    @Test
    void generatedTransportMustNotBeCommittedUnderMainSources() throws IOException {
        Path mainSources = Path.of("src/main/java");

        if (!Files.exists(mainSources)) {
            return;
        }

        try (Stream<Path> files = Files.walk(mainSources)) {
            boolean committedGeneratedSource = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .anyMatch(path -> {
                        try {
                            String content = Files.readString(path);
                            return content.contains(
                                    "package com.regional.corebanking.generated."
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

            assertFalse(
                    committedGeneratedSource,
                    "Generated OpenAPI transport sources must not be committed under src/main/java"
            );
        }
    }
}
