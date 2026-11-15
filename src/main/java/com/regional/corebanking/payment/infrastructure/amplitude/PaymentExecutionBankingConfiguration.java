package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentEventNumberReservationPort;
import com.regional.corebanking.payment.application.port.out.PaymentBankEventTrackingPort;
import org.springframework.beans.factory.ObjectProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
class PaymentExecutionBankingConfiguration {
    @Bean
    PaymentExecutionSqlDialect paymentExecutionSqlDialect(@Value("${regional.banking.database.vendor:informix}") String v) {
        return switch (v.strip().toLowerCase()) {
            case "informix" -> new InformixPaymentExecutionSqlDialect();
            case "oracle" -> new OraclePaymentExecutionSqlDialect();
            default -> throw new IllegalArgumentException("Unsupported regional.banking.database.vendor: " + v);
        };
    }

    @Bean
    PaymentExecutionBankingPort paymentExecutionBankingPort(PaymentExecutionSqlDialect d, @Value("${regional.banking.financial-institution-code:}") String i, @Value("${regional.banking.datasource.url:}") String u, @Value("${regional.banking.datasource.username:}") String n, @Value("${regional.banking.datasource.password:}") String p, @Value("${regional.banking.datasource.driver-class-name:}") String x, ObjectProvider<PaymentEventNumberReservationPort> reservations, ObjectProvider<PaymentBankEventTrackingPort> tracking) {
        if (u.isBlank()) return new UnconfiguredPaymentExecutionBankingAdapter();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(u);
        ds.setUsername(n);
        ds.setPassword(p);
        if (!x.isBlank()) ds.setDriverClassName(x);
        return new JdbcPaymentExecutionBankingAdapter(ds, d, i, BkeveIndicativeBalanceUpdate.unconfigured(), reservations.getIfAvailable(), tracking.getIfAvailable());
    }
}
