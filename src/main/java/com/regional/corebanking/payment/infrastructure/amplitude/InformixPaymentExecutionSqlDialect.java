package com.regional.corebanking.payment.infrastructure.amplitude;
import java.sql.*; final class InformixPaymentExecutionSqlDialect extends AbstractPaymentExecutionSqlDialect { public void prepareConnection(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.execute("SET ISOLATION TO DIRTY READ");}}}
