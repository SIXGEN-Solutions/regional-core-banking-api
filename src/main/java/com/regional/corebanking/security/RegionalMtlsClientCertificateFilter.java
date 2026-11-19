package com.regional.corebanking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Admission control when regional.security.mtls.enabled is true.
 * The servlet container first validates the X.509 chain against the configured
 * truststore and requires proof of possession during the TLS handshake.
 * This filter further limits API access to explicitly approved certificates.
 * It does not replace business-level authorization decisions.
 */
@Component
@ConditionalOnProperty(
        prefix = "regional.security.mtls",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RegionalMtlsClientCertificateFilter extends OncePerRequestFilter {

    private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    private final List<byte[]> permittedFingerprints;

    public RegionalMtlsClientCertificateFilter(
            @Value("${regional.security.mtls.allowed-client-cert-sha256}") String allowlist) {
        if (allowlist == null || allowlist.isBlank()) {
            throw new IllegalArgumentException("Regional mTLS certificate allowlist must not be empty");
        }
        this.permittedFingerprints = Arrays.stream(allowlist.split(",", -1))
                .map(String::trim)
                .map(RegionalMtlsClientCertificateFilter::parseFingerprint)
                .toList();
    }

    private static byte[] parseFingerprint(String candidate) {
        String normalized = candidate.replace(":", "").replace(" ", "");
        if (!normalized.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 fingerprint in Regional mTLS allowlist");
        }
        return HexFormat.of().parseHex(normalized);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Do not trust forwarded headers or a plain HTTP transport to assert TLS identity.
        if (!request.isSecure()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Object attribute = request.getAttribute(CERTIFICATE_ATTRIBUTE);
        if (!(attribute instanceof X509Certificate[] certificates)
                || certificates.length == 0 || certificates[0] == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        final byte[] actual;
        try {
            actual = MessageDigest.getInstance("SHA-256").digest(certificates[0].getEncoded());
        } catch (NoSuchAlgorithmException | CertificateEncodingException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        boolean allowed = permittedFingerprints.stream()
                .anyMatch(expected -> MessageDigest.isEqual(expected, actual));
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
