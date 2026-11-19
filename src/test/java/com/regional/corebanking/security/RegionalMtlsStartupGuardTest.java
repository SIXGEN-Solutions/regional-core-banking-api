package com.regional.corebanking.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalMtlsStartupGuardTest {
    @Test void localHttpAllowed() {
        assertDoesNotThrow(() -> new RegionalMtlsStartupGuard(false, false).verify());
    }
    @Test void secureDeploymentRejectsDowngrade() {
        assertThrows(IllegalStateException.class,
                () -> new RegionalMtlsStartupGuard(true, false).verify());
    }
    @Test void secureDeploymentAllowsMtls() {
        assertDoesNotThrow(() -> new RegionalMtlsStartupGuard(true, true).verify());
    }
}
