package com.regional.corebanking.confirmation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.api.PaymentConfirmationApiMapper;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.application.port.out.*;
import com.regional.corebanking.confirmation.application.service.PaymentConfirmationService;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

@Configuration
public class ConfirmationConfiguration {
    @Bean
    ChallengeRepository confirmationChallengeRepository() {
        return new InMemoryChallengeRepository();
    }

    @Bean
    IdempotencyRepository confirmationIdempotencyRepository() {
        return new InMemoryIdempotencyRepository();
    }

    @Bean
    OtpSecurityPort confirmationOtpSecurityPort() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new HmacOtpSecurityAdapter(key, "runtime-local");
    }

    @Bean
    ConfirmationDeliveryPort confirmationDeliveryPort() {
        return new NoopConfirmationDeliveryAdapter(
                EnumSet.of(DeliveryChannel.SMS, DeliveryChannel.EMAIL));
    }

    @Bean
    PaymentConfirmationApiMapper paymentConfirmationApiMapper(ObjectMapper objectMapper) {
        return new PaymentConfirmationApiMapper(objectMapper);
    }

    @Bean
    PaymentConfirmationUseCase paymentConfirmationUseCase(
            ChallengeRepository challenges,
            IdempotencyRepository idempotency,
            OtpSecurityPort otp,
            ConfirmationDeliveryPort delivery
    ) {
        return new PaymentConfirmationService(
                challenges, idempotency, otp, delivery,
                Clock.systemUTC(), Duration.ofMinutes(5), 3, 3);
    }
}
