package com.regional.corebanking.customer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.api.CustomerApiMapper;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.application.service.CustomerVerificationService;
import com.regional.corebanking.customer.infrastructure.amplitude.*;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerConfiguration {
    @Bean
    CustomerSqlDialect customerSqlDialect(
            @Value("${regional.banking.database.vendor:informix}") String vendor) {
        return switch (vendor.strip().toLowerCase()) {
            case "informix" -> new InformixCustomerSqlDialect();
            case "oracle" -> new OracleCustomerSqlDialect();
            default -> throw new IllegalArgumentException("Unsupported regional.banking.database.vendor: " + vendor);
        };
    }

    @Bean
    CustomerBankingPort customerBankingPort(
            CustomerSqlDialect dialect,
            @Value("${regional.banking.financial-institution-code:}") String institutionCode,
            @Value("${regional.banking.datasource.url:}") String url,
            @Value("${regional.banking.datasource.username:}") String username,
            @Value("${regional.banking.datasource.password:}") String password,
            @Value("${regional.banking.datasource.driver-class-name:}") String driver) {
        if (url.isBlank()) return new UnconfiguredCustomerBankingAdapter();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        if (!driver.isBlank()) dataSource.setDriverClassName(driver);
        return new JdbcCustomerBankingAdapter(dataSource, dialect, institutionCode);
    }

    @Bean CustomerApiMapper customerApiMapper(ObjectMapper objectMapper) {
        return new CustomerApiMapper(objectMapper);
    }

    @Bean CustomerVerificationService customerVerificationService(CustomerBankingPort port) {
        return new CustomerVerificationService(port);
    }
}
