package com.regional.corebanking.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovedContractGenerationGuardTest {

    private static final Path CONTRACT =
            Path.of("contracts/openapi/regional-core-banking-api-v1.yaml");

    @Test
    void contractMustBeApprovedBeforeGeneration() throws IOException {
        String yaml = Files.readString(CONTRACT);

        assertTrue(yaml.contains("approvalStatus: APPROVED"));
        assertTrue(yaml.contains("codeGenerationAllowed: true"));
        assertTrue(yaml.contains("sourceOfTruth: THIS_REPOSITORY_AFTER_APPROVAL"));
    }
}
