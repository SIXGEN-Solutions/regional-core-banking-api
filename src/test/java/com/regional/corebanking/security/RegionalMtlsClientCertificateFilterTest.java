package com.regional.corebanking.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionalMtlsClientCertificateFilterTest {
    private static final byte[] APPROVED = "regional-sixpay-test-cert".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsApprovedClientCertificateOnSecureTransport() throws Exception {
        var filter = new RegionalMtlsClientCertificateFilter(fingerprint(APPROVED));
        var request = request(true, certificate(APPROVED));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    void rejectsMissingClientCertificate() throws Exception {
        var filter = new RegionalMtlsClientCertificateFilter(fingerprint(APPROVED));
        var request = request(true, null);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsUnapprovedClientCertificate() throws Exception {
        var filter = new RegionalMtlsClientCertificateFilter(fingerprint(APPROVED));
        var request = request(true, certificate("another-cert".getBytes(StandardCharsets.UTF_8)));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsPlainHttpEvenIfCertificateAttributeIsInjected() throws Exception {
        var filter = new RegionalMtlsClientCertificateFilter(fingerprint(APPROVED));
        var request = request(false, certificate(APPROVED));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsEmptyOrMalformedAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> new RegionalMtlsClientCertificateFilter(""));
        assertThrows(IllegalArgumentException.class, () -> new RegionalMtlsClientCertificateFilter("bad"));
    }

    @Test
    void permitsTwoFingerprintsForCertificateRotation() throws Exception {
        var filter = new RegionalMtlsClientCertificateFilter(
                fingerprint("old-cert".getBytes(StandardCharsets.UTF_8)) + "," + fingerprint(APPROVED));
        var chain = new MockFilterChain();
        filter.doFilter(request(true, certificate(APPROVED)), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }

    private static MockHttpServletRequest request(boolean secure, X509Certificate cert) {
        var request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.setSecure(secure);
        if (cert != null) {
            request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{cert});
        }
        return request;
    }

    private static X509Certificate certificate(byte[] bytes) throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getEncoded()).thenReturn(bytes);
        return cert;
    }

    private static String fingerprint(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
