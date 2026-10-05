package com.regional.corebanking.confirmation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.api.PaymentConfirmationApiMapper;
import com.regional.corebanking.confirmation.application.port.in.PaymentConfirmationUseCase;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import com.regional.corebanking.confirmation.application.port.out.OtpSecurityPort;
import com.regional.corebanking.confirmation.application.service.PaymentConfirmationService;
import com.regional.corebanking.confirmation.infrastructure.email.BankingConfirmationRecipientAdapter;
import com.regional.corebanking.confirmation.infrastructure.email.EmailConfirmationDeliveryAdapter;
import com.regional.corebanking.confirmation.infrastructure.sms.KannelSmsConfirmationDeliveryAdapter;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
            @Value("${regional.confirmation.email.test-recipient}") String emailRecipient,
            @Value("${regional.confirmation.sms.test-recipient:237600000000}") String phoneRecipient) {
        return new ConfirmationRecipientPort() {
            @Override
            public Optional<String> findEmail(String financialInstitutionCode, String customerReference) {
                return Optional.ofNullable(emailRecipient).map(String::strip).filter(v -> !v.isEmpty());
            }

            @Override
            public Optional<String> findPhone(String financialInstitutionCode, String customerReference) {
                return Optional.ofNullable(phoneRecipient).map(String::strip).filter(v -> !v.isEmpty());
            }
        };
    }

    @Bean
    @Profile("!local")
    ConfirmationRecipientPort bankingConfirmationRecipientPort(CustomerBankingPort customerBankingPort) {
        return new BankingConfirmationRecipientAdapter(customerBankingPort);
    }

    @Bean
    ConfirmationDeliveryPort emailConfirmationDeliveryPort(
            JavaMailSender mailSender,
            ConfirmationRecipientPort recipientPort,
            @Value("${regional.confirmation.email.sender}") String sender,
            @Value("${regional.confirmation.email.enabled:false}") boolean enabled) {
        return new EmailConfirmationDeliveryAdapter(mailSender, recipientPort, sender, enabled);
    }

    @Bean
    RestClient kannelRestClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ConfirmationDeliveryPort smsConfirmationDeliveryPort(
            RestClient kannelRestClient,
            ConfirmationRecipientPort recipientPort,
            @Value("${regional.confirmation.sms.base-url:}") String baseUrl,
            @Value("${regional.confirmation.sms.username:}") String username,
            @Value("${regional.confirmation.sms.password:}") String password,
            @Value("${regional.confirmation.sms.sender:LAREGIONALE}") String sender,
            @Value("${regional.confirmation.sms.enabled:false}") boolean enabled) {
        return new KannelSmsConfirmationDeliveryAdapter(
                kannelRestClient,
                recipientPort,
                baseUrl,
                username,
                password,
                sender,
                enabled
        );
    }

    @Bean
    ConfirmationDeliveryPort confirmationDeliveryPort(
            @Qualifier("emailConfirmationDeliveryPort") ConfirmationDeliveryPort email,
            @Qualifier("smsConfirmationDeliveryPort") ConfirmationDeliveryPort sms) {
        return new CompositeConfirmationDeliveryAdapter(List.of(email, sms));
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
            @Qualifier("confirmationDeliveryPort") ConfirmationDeliveryPort delivery
    ) {
        return new PaymentConfirmationService(
                challenges,
                idempotency,
                otp,
                delivery,
                Clock.systemUTC(),
                Duration.ofMinutes(5),
                3,
                3
        );
    }
}
