package com.regional.corebanking.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiNoGenerationProfileTest {

    @Test
    void noGenerationProfileIsActuallyWiredToGeneratorSkipParameter() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("<openapi.generator.skip>false</openapi.generator.skip>")
                .contains("<skip>${openapi.generator.skip}</skip>")
                .contains("<id>r3-no-openapi-generation</id>")
                .contains("<openapi.generator.skip>true</openapi.generator.skip>");
    }
}
