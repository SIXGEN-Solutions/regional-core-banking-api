package com.regional.corebanking.customer.infrastructure;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.application.service.CustomerVerificationService;
import com.regional.corebanking.customer.infrastructure.amplitude.CustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.InformixCustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.JdbcCustomerBankingAdapter;
import com.regional.corebanking.customer.infrastructure.amplitude.OracleCustomerSqlDialect;
import com.regional.corebanking.customer.infrastructure.amplitude.UnconfiguredCustomerBankingAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnBean(DataSource.class)
    CustomerBankingPort jdbcCustomerBankingPort(
            DataSource dataSource,
            CustomerSqlDialect dialect,
            @Value("${regional.banking.financial-institution-code:}") String institutionCode
    ) {
        return new JdbcCustomerBankingAdapter(dataSource, dialect, institutionCode);
    }

    @Bean
    @ConditionalOnMissingBean(CustomerBankingPort.class)
    CustomerBankingPort unconfiguredCustomerBankingPort() {
        return new UnconfiguredCustomerBankingAdapter();
    }

    @Bean
    CustomerVerificationService customerVerificationService(CustomerBankingPort customerBankingPort) {
        return new CustomerVerificationService(customerBankingPort);
    }
}
