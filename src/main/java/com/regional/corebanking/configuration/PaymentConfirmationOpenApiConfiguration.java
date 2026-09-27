package com.regional.corebanking.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfirmationOpenApiConfiguration {
    @Bean
    GroupedOpenApi paymentConfirmationOpenApi() {
        return GroupedOpenApi.builder()
                .group("payment-confirmation")
                .pathsToMatch(
                        "/api/v1/payment-confirmation-challenges/**",
                        "/api/v1/payment-confirmation-challenge-lookups/**"
                )
                .build();
    }
}
