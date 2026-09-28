package com.regional.corebanking.confirmation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.api.PaymentConfirmationApiMapper;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.application.port.out.*;
import com.regional.corebanking.confirmation.application.service.PaymentConfirmationService;
import com.regional.corebanking.confirmation.infrastructure.email.EmailConfirmationDeliveryAdapter;
import com.regional.corebanking.confirmation.infrastructure.email.BankingConfirmationRecipientAdapter;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Configuration
public class ConfirmationConfiguration {
    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "jdbc",
            matchIfMissing = true)
    TransactionTemplate confirmationTransactionTemplate(
            @Qualifier("technicalTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "jdbc",
            matchIfMissing = true)
    ChallengeRepository jdbcConfirmationChallengeRepository(
            @Qualifier("technicalJdbcTemplate") JdbcTemplate jdbc,
            TransactionTemplate tx) {
        return new JdbcChallengeRepository(jdbc, tx);
    }

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "jdbc",
            matchIfMissing = true)
    IdempotencyRepository jdbcConfirmationIdempotencyRepository(
            @Qualifier("technicalJdbcTemplate") JdbcTemplate jdbc,
            TransactionTemplate tx,
            ObjectMapper objectMapper) {
        return new JdbcIdempotencyRepository(jdbc, tx, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "memory")
    ChallengeRepository inMemoryConfirmationChallengeRepository() {
        return new InMemoryChallengeRepository();
    }

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "memory")
    IdempotencyRepository inMemoryConfirmationIdempotencyRepository() {
        return new InMemoryIdempotencyRepository();
    }

    @Bean
    OtpSecurityPort confirmationOtpSecurityPort(
            @Value("${regional.confirmation.hmac.active-key-version}") String activeKeyVersion,
            @Value("${regional.confirmation.hmac.keys}") String encodedKeys) {
        return new HmacOtpSecurityAdapter(OtpHmacKeyRingConfiguration.parse(encodedKeys), activeKeyVersion);
    }

    @Bean
    @Profile("local")
    ConfirmationRecipientPort localConfirmationRecipientPort(
            @Value("${regional.confirmation.email.test-recipient}") String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "regional.confirmation.email.test-recipient is required for local");
        }
        return (financialInstitutionCode, customerReference) -> Optional.of(recipient);
    }

    @Bean
    @Profile("!local")
    ConfirmationRecipientPort bankingConfirmationRecipientPort(
            CustomerBankingPort customerBankingPort) {
        return new BankingConfirmationRecipientAdapter(customerBankingPort);
    }

    @Bean
    ConfirmationDeliveryPort confirmationDeliveryPort(
            JavaMailSender mailSender,
            ConfirmationRecipientPort recipientPort,
            @Value("${regional.confirmation.email.sender}") String sender,
            @Value("${regional.confirmation.email.enabled:false}") boolean enabled) {
        return new EmailConfirmationDeliveryAdapter(
                mailSender, recipientPort, sender, enabled);
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
