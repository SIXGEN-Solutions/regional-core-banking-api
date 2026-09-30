package com.regional.corebanking.payment.infrastructure.amplitude;
import java.sql.Connection; import java.sql.SQLException;
interface PaymentExecutionSqlDialect { String findActiveAccount(); String findCustomerOpposition(); String findAccountOpposition(); String findOperationOpposition(); String findAvailableFunds(); String findAccountingDatePrimary(); String findAccountingDateFallback(); String findNightMode(); default void prepareConnection(Connection c) throws SQLException {} }
