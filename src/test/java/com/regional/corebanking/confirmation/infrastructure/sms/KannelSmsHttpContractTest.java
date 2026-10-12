package com.regional.corebanking.confirmation.infrastructure.sms;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import com.regional.corebanking.confirmation.domain.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KannelSmsHttpContractTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> received = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private void start(String body, int code, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/sendsms", exchange -> {
            calls.incrementAndGet();
            received.set(exchange.getRequestURI().getRawQuery());
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(code, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // Client can time out while the simulated Kannel processes a request.
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    private KannelSmsConfirmationDeliveryAdapter adapter(int readTimeoutMs) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient rest = RestClient.builder().requestFactory(factory).build();
        ConfirmationRecipientPort recipient = new ConfirmationRecipientPort() {
            @Override public Optional<String> findEmail(String bank, String customer) { return Optional.empty(); }
            @Override public Optional<String> findPhone(String bank, String customer) { return Optional.of("699123456"); }
        };
        return new KannelSmsConfirmationDeliveryAdapter(rest, recipient,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-user", "test-password", "LAREGIONALE", true);
    }

    private ConfirmationChallenge challenge() {
        return new ConfirmationChallenge("CHL-TEST", "PAY-TEST", "CUS-TEST", "ACC-TEST",
                BigDecimal.ONE, "XAF", "verifier", "v1", ChallengeStatus.ACTIVE,
                ConfirmationBusinessCode.CHALLENGE_ACTIVE, 0, 0,
                java.util.Set.of(DeliveryChannel.SMS), Instant.now(), Instant.now().plusSeconds(300),
                null, null, null);
    }

    private static Map<String, String> params(String query) {
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("=", 2))
                .collect(Collectors.toMap(a -> URLDecoder.decode(a[0], StandardCharsets.UTF_8),
                        a -> URLDecoder.decode(a[1], StandardCharsets.UTF_8)));
    }

    @Test void accepts_only_confirmed_kannel_success_and_sends_bank_parameters() throws Exception {
        start("0: Accepted for delivery", 200, 0);
        assertThat(adapter(2000).dispatch("REGIONAL", challenge(), "123456".toCharArray()))
                .isEqualTo(ConfirmationDeliveryPort.Outcome.ACCEPTED);
        assertThat(calls.get()).isEqualTo(1);
        Map<String, String> query = params(received.get());
        assertThat(query).containsEntry("user", "test-user").containsEntry("password", "test-password")
                .containsEntry("from", "LAREGIONALE").containsEntry("to", "237699123456")
                .containsEntry("text", "Votre code est 123456");
    }

    @Test void explicit_kannel_rejection_is_confirmed_failure() throws Exception {
        start("3: Invalid login", 200, 0);
        assertThat(adapter(2000).dispatch("REGIONAL", challenge(), "123456".toCharArray()))
                .isEqualTo(ConfirmationDeliveryPort.Outcome.CONFIRMED_FAILURE);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void timeout_is_unknown_and_is_not_replayed() throws Exception {
        start("0: Accepted for delivery", 200, 1200);
        assertThat(adapter(100).dispatch("REGIONAL", challenge(), "123456".toCharArray()))
                .isEqualTo(ConfirmationDeliveryPort.Outcome.UNKNOWN);
        Thread.sleep(200);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void non_success_http_status_is_not_assumed_to_be_definite_rejection() throws Exception {
        start("upstream unavailable", 502, 0);
        assertThat(adapter(2000).dispatch("REGIONAL", challenge(), "123456".toCharArray()))
                .isEqualTo(ConfirmationDeliveryPort.Outcome.UNKNOWN);
        assertThat(calls.get()).isEqualTo(1);
    }
}
