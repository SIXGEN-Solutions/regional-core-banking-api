package com.regional.corebanking.customer.infrastructure.amplitude;

import java.sql.Connection;
import java.sql.SQLException;

public interface CustomerSqlDialect {

    String searchByCustomerNumber();

    String searchByNiu();

    String searchByCustomerNumberAndNiu();

    String findIdentityByCustomerReference();

    String findAccountsByCustomerReference();

    String findAccountByReferenceParts();

    String checkNightMode();

    String availableBalanceNormal();

    String availableBalanceNight();

    String findPhonesByCustomerReference();

    String findEmailsByCustomerReference();

    default void prepareConnection(Connection connection) throws SQLException {
        // no-op by default
    }
}
