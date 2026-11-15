package com.regional.corebanking.payment.infrastructure.amplitude;

final class OraclePaymentExecutionSqlDialect extends AbstractPaymentExecutionSqlDialect {
    @Override
    public String findHolidays() {
        return "select jourfer from bkfer where TO_CHAR(jourfer, 'YYYY')=? ";
    }
}
