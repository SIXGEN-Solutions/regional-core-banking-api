package com.regional.corebanking.payment.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionPersistencePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PaymentExecutionPersistenceConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "jdbc",
            matchIfMissing = true)
    PaymentExecutionPersistencePort jdbcPaymentExecutionPersistencePort(
            @Qualifier("technicalJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("technicalTransactionManager") PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        return new JdbcPaymentExecutionPersistenceAdapter(
                jdbc,
                transactionManager,
                objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            name = "regional.confirmation.persistence",
            havingValue = "memory")
    PaymentExecutionPersistencePort inMemoryPaymentExecutionPersistencePort() {
        return new InMemoryPaymentExecutionPersistenceAdapter();
    }

}
