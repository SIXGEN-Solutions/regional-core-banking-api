package com.regional.corebanking.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RegionalOpenApiMtlsOnlyContractTest {
    @Test
    void canonicalContractUsesMtlsWithoutOauth2ForSixpayCalls() throws Exception {
        String yaml = Files.readString(Path.of("contracts/openapi/regional-core-banking-api-v1.yaml"));
        assertTrue(yaml.contains("security:\n  - mutualTLS: []"));
        assertTrue(yaml.contains("    mutualTLS:\n      type: mutualTLS"));
        assertFalse(yaml.contains("oauth2ClientCredentials"));
        assertFalse(yaml.contains("type: oauth2"));
    }

    @Test
    void swaggerCopyMatchesCanonicalContract() throws Exception {
        String canonical = Files.readString(Path.of("contracts/openapi/regional-core-banking-api-v1.yaml"));
        String swagger = Files.readString(Path.of("src/main/resources/static/openapi/regional-core-banking-api-v1.yaml"));
        assertEquals(canonical.replace("\r\n", "\n"), swagger.replace("\r\n", "\n"));
    }
}
