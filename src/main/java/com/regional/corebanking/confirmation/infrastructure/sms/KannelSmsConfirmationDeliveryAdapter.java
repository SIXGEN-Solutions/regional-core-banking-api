package com.regional.corebanking.confirmation.infrastructure.sms;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

public final class KannelSmsConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {

    private static final String ACCEPTED_RESPONSE = "0: Accepted for delivery";

    private final RestClient restClient;
    private final ConfirmationRecipientPort recipients;
    private final URI baseUri;
    private final String username;
    private final String password;
    private final String sender;
    private final boolean enabled;

    public KannelSmsConfirmationDeliveryAdapter(
            RestClient restClient,
            ConfirmationRecipientPort recipients,
            String baseUrl,
            String username,
            String password,
            String sender,
            boolean enabled
    ) {
        this.restClient = restClient;
        this.recipients = recipients;
        this.baseUri = enabled ? URI.create(require(baseUrl, "Kannel base URL")) : URI.create("http://localhost");
        this.username = enabled ? require(username, "Kannel username") : username;
        this.password = enabled ? require(password, "Kannel password") : password;
        this.sender = enabled ? require(sender, "Kannel sender") : sender;
        this.enabled = enabled;
    }

    @Override
    public Set<DeliveryChannel> enabledChannels() {
        return enabled ? Set.of(DeliveryChannel.SMS) : Set.of();
    }

    @Override
    public Outcome dispatch(String institution, ConfirmationChallenge challenge, char[] otp) {
        if (!enabled) {
            throw new IllegalStateException("SMS OTP delivery is disabled");
        }

        Optional<String> rawPhone = recipients.findPhone(institution, challenge.customerReference());
        if (rawPhone.isEmpty()) {
            return Outcome.CONFIRMED_FAILURE;
        }

        final String phone;
        try {
            phone = toKannelPhone(rawPhone.get());
        } catch (IllegalArgumentException invalidPhone) {
            return Outcome.CONFIRMED_FAILURE;
        }

        String message = "Votre code est " + new String(otp);

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(baseUri.getScheme())
                            .host(baseUri.getHost())
                            .port(resolvePort(baseUri))
                            .path(resolveBasePath(baseUri) + "/cgi-bin/sendsms")
                            .queryParam("user", username)
                            .queryParam("password", password)
                            .queryParam("from", sender)
                            .queryParam("to", phone)
                            .queryParam("text", message)
                            .build())
                    .retrieve()
                    .body(String.class);

            return response != null && response.trim().startsWith(ACCEPTED_RESPONSE)
                    ? Outcome.ACCEPTED
                    : Outcome.CONFIRMED_FAILURE;
        } catch (ResourceAccessException uncertainTransportFailure) {
            return Outcome.UNKNOWN;
        } catch (RuntimeException explicitFailure) {
            return Outcome.CONFIRMED_FAILURE;
        }
    }

    static String toKannelPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }

        String normalized = raw.replaceAll("[\\s().-]", "");
        if (normalized.startsWith("+237")) {
            normalized = normalized.substring(1);
        } else if (!normalized.startsWith("237")) {
            normalized = "237" + normalized;
        }

        if (!normalized.matches("2376\\d{8}")) {
            throw new IllegalArgumentException("unsupported Cameroon mobile number format");
        }
        return normalized;
    }

    private static int resolvePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String resolveBasePath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) return "";
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required when SMS delivery is enabled");
        }
        return value.strip();
    }
}
