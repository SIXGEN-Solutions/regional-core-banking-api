package com.regional.corebanking.customer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.api.CustomerApiMapper;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.application.service.CustomerVerificationService;
import com.regional.corebanking.customer.infrastructure.amplitude.CustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.InformixCustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.JdbcCustomerBankingAdapter;
import com.regional.corebanking.customer.infrastructure.amplitude.OracleCustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.UnconfiguredCustomerBankingAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class CustomerConfiguration {

    @Bean
    CustomerSqlDialect customerSqlDialect(
            @Value("${regional.banking.database.vendor:informix}") String vendor
    ) {
        return switch (vendor.strip().toLowerCase()) {
            case "informix" -> new InformixCustomerSqlDialect();
            case "oracle" -> new OracleCustomerSqlDialect();
            default -> throw new IllegalArgumentException(
                    "Unsupported regional.banking.database.vendor: " + vendor
            );
        };
    }

    @Bean
    CustomerBankingPort customerBankingPort(
            ObjectProvider<DataSource> dataSourceProvider,
            CustomerSqlDialect dialect,
            @Value("${regional.banking.financial-institution-code:}") String institutionCode
    ) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();

        if (dataSource == null) {
            return new UnconfiguredCustomerBankingAdapter();
        }

        return new JdbcCustomerBankingAdapter(dataSource, dialect, institutionCode);
    }

    @Bean
    CustomerApiMapper customerApiMapper(ObjectMapper objectMapper) {
        return new CustomerApiMapper(objectMapper);
    }

    @Bean
    CustomerVerificationService customerVerificationService(CustomerBankingPort customerBankingPort) {
        return new CustomerVerificationService(customerBankingPort);
    }
}
