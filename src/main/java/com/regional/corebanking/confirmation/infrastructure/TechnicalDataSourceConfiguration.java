package com.regional.corebanking.confirmation.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(
        name = "regional.confirmation.persistence",
        havingValue = "jdbc",
        matchIfMissing = true
)
public class TechnicalDataSourceConfiguration {
    @Bean(name = "technicalDataSource")
    DataSource technicalDataSource(
            @Value("${regional.technical-datasource.url}") String url,
            @Value("${regional.technical-datasource.username}") String username,
            @Value("${regional.technical-datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setSchema("core_banking");
        return ds;
    }

    @Bean(initMethod = "migrate")
    Flyway technicalFlyway(@Qualifier("technicalDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("core_banking")
                .defaultSchema("core_banking")
                .locations("classpath:db/migration")
                .load();
    }

    @Bean(name = "technicalJdbcTemplate")
    JdbcTemplate technicalJdbcTemplate(@Qualifier("technicalDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "technicalTransactionManager")
    PlatformTransactionManager technicalTransactionManager(
            @Qualifier("technicalDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
