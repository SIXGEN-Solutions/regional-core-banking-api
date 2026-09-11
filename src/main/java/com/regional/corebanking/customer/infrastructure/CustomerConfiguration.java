package com.regional.corebanking.customer.infrastructure;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.application.service.CustomerVerificationService;
import com.regional.corebanking.customer.infrastructure.amplitude.UnconfiguredCustomerBankingAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerConfiguration {

    @Bean
    CustomerBankingPort customerBankingPort() {
        return new UnconfiguredCustomerBankingAdapter();
    }

    @Bean
    CustomerVerificationService customerVerificationService(CustomerBankingPort customerBankingPort) {
        return new CustomerVerificationService(customerBankingPort);
    }
}
