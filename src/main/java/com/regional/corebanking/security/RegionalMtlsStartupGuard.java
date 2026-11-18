package com.regional.corebanking.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Independent bank-facing mandate: refuse HTTP even if ENABLE_MTLS is misconfigured. */
@Component
public final class RegionalMtlsStartupGuard {
    private final boolean required;
    private final boolean enabled;

    public RegionalMtlsStartupGuard(
            @Value("${regional.security.mtls.required:false}") boolean required,
            @Value("${regional.security.mtls.enabled:false}") boolean enabled) {
        this.required = required;
        this.enabled = enabled;
    }

    @PostConstruct
    public void verify() {
        if (required && !enabled) {
            throw new IllegalStateException("Regional secure deployment requires ENABLE_MTLS=true");
        }
    }
}
