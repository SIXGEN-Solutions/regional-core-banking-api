package com.regional.corebanking.confirmation.application.service;

import com.regional.corebanking.confirmation.domain.ConfirmationCommand;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfirmationRecoveryTest {
    @Test
    void createResultIsRecoverableByOriginalKey() {
        PaymentConfirmationService service = service();
        var created = service.create("REGIONAL", "create-recovery-key", command());
        var recovered = service.recover("REGIONAL", "create-recovery-key");
        assertThat(recovered.challengeReference()).isEqualTo(created.challengeReference());
    }

    @Test
    void replacementResultIsRecoverableByOriginalReplacementKey() {
        PaymentConfirmationService service = service();
        var created = service.create("REGIONAL", "create-key", command());
        var replacement = service.replace(
                "REGIONAL", "replacement-recovery-key", created.challengeReference(),
                new ConfirmationCommand.Replace(created.paymentReference()));
        var recovered = service.recover("REGIONAL", "replacement-recovery-key");
        assertThat(recovered.challengeReference()).isEqualTo(replacement.challengeReference());
    }

    private PaymentConfirmationService service() {
        return new PaymentConfirmationService(
                new InMemoryChallengeRepository(),
                new InMemoryIdempotencyRepository(),
                new HmacOtpSecurityAdapter(new byte[32], "test-v1"),
                new NoopConfirmationDeliveryAdapter(Set.of(DeliveryChannel.SMS)),
                Clock.systemUTC(), Duration.ofMinutes(5), 3, 3);
    }

    private ConfirmationCommand.Create command() {
        return new ConfirmationCommand.Create(
                "PAY-0123456789ABCDEFGHJKMNPQRS", "CUS-001", "001-123456-7",
                new BigDecimal("1000.00"), "XAF");
    }
}
