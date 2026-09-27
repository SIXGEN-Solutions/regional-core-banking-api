package com.regional.corebanking.confirmation.application.service;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.ConfirmationCommand;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfirmationConcurrencyTest {
    @Test
    void concurrentCreateWithSameIdempotencyKeyExecutesDeliveryOnce() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        ConfirmationDeliveryPort delivery = new ConfirmationDeliveryPort() {
            public Set<DeliveryChannel> enabledChannels() { return Set.of(DeliveryChannel.SMS); }
            public Outcome dispatch(com.regional.corebanking.confirmation.domain.ConfirmationChallenge c, char[] otp) {
                deliveries.incrementAndGet();
                return Outcome.DELIVERED;
            }
        };

        PaymentConfirmationService service = new PaymentConfirmationService(
                new InMemoryChallengeRepository(),
                new InMemoryIdempotencyRepository(),
                new HmacOtpSecurityAdapter(new byte[32], "test-v1"),
                delivery, Clock.systemUTC(), Duration.ofMinutes(5), 3, 3);

        ConfirmationCommand.Create command = new ConfirmationCommand.Create(
                "PAY-0123456789ABCDEFGHJKMNPQRS", "CUS-001", "001-123456-7",
                new BigDecimal("1000.00"), "XAF");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> (Callable<String>) () ->
                            service.create("REGIONAL", "same-create-key", command).challengeReference())
                    .toList();
            Set<String> refs = new HashSet<>();
            for (Future<String> f : executor.invokeAll(tasks)) {
                refs.add(f.get(5, TimeUnit.SECONDS));
            }
            assertThat(refs).hasSize(1);
            assertThat(deliveries.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
