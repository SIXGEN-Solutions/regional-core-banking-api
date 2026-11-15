package com.regional.corebanking.confirmation.application.service;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.ConfirmationBusinessCode;
import com.regional.corebanking.confirmation.domain.ConfirmationCommand;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import com.regional.corebanking.confirmation.domain.DeliveryStatus;
import com.regional.corebanking.confirmation.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfirmationDeliverySemanticsTest {
    @Test
    void confirmedFailureMapsToDeliveryFailed() {
        assertThat(service(ConfirmationDeliveryPort.Outcome.CONFIRMED_FAILURE)
                .create("REGIONAL", "key-failure", command())
                .businessCode()).isEqualTo(ConfirmationBusinessCode.DELIVERY_FAILED);
    }

    @Test
    void unknownDeliveryMapsToDependencyResultUnknown() {
        assertThat(service(ConfirmationDeliveryPort.Outcome.UNKNOWN)
                .create("REGIONAL", "key-unknown", command())
                .businessCode()).isEqualTo(ConfirmationBusinessCode.DEPENDENCY_RESULT_UNKNOWN);
    }

    @Test
    void acceptedDeliveryHasRealSentAt() {
        var c=service(ConfirmationDeliveryPort.Outcome.ACCEPTED).create("REGIONAL","key-accepted",command());
        assertThat(c.deliveryStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(c.deliveryRequestedAt()).isNotNull();
        assertThat(c.sentAt()).isNotNull();
        assertThat(c.sentAt()).isAfterOrEqualTo(c.deliveryRequestedAt());
    }

    @Test
    void unknownDeliveryHasNoSentAt() {
        var c=service(ConfirmationDeliveryPort.Outcome.UNKNOWN).create("REGIONAL","key-unknown-sent-at",command());
        assertThat(c.deliveryStatus()).isEqualTo(DeliveryStatus.UNKNOWN);
        assertThat(c.deliveryRequestedAt()).isNotNull();
        assertThat(c.sentAt()).isNull();
    }

    private PaymentConfirmationService service(ConfirmationDeliveryPort.Outcome outcome) {
        return new PaymentConfirmationService(
                new InMemoryChallengeRepository(),
                new InMemoryIdempotencyRepository(),
                new HmacOtpSecurityAdapter(new byte[32], "test-v1"),
                new ConfirmationDeliveryPort() {
                    public Set<DeliveryChannel> enabledChannels() {
                        return Set.of(DeliveryChannel.SMS);
                    }

                    public Outcome dispatch(
                            String financialInstitutionCode,
                            com.regional.corebanking.confirmation.domain.ConfirmationChallenge c,
                            char[] otp) {
                        return outcome;
                    }
                },
                Clock.systemUTC(), Duration.ofMinutes(5), 3, 3);
    }

    private ConfirmationCommand.Create command() {
        return new ConfirmationCommand.Create(
                "PAY-0123456789ABCDEFGHJKMNPQRS", "CUS-001", "001-123456-7",
                new BigDecimal("1000.00"), "XAF");
    }
}
