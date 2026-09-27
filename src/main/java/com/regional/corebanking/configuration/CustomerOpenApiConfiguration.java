package com.regional.corebanking.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerOpenApiConfiguration {

    @Bean
    GroupedOpenApi customerOpenApi() {
        return GroupedOpenApi.builder()
                .group("customer")
                .pathsToMatch(
                        "/api/v1/customers",
                        "/api/v1/customers/**",
                        "/api/v1/customer-verifications"
                )
                .build();
    }
}
