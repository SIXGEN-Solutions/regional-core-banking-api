package com.regional.corebanking.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentExecutionOpenApiConfiguration {

    @Bean
    GroupedOpenApi paymentExecutionOpenApi() {
        return GroupedOpenApi.builder()
                .group("payment-execution")
                .pathsToMatch(
                        "/api/v1/payment-events",
                        "/api/v1/payment-events/**"
                )
                .build();
    }
}
