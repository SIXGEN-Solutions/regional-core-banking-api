package com.regional.corebanking.payment.infrastructure.amplitude;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Exact SQL/parameter sequence of legacy processPOST + RequeteSQLCBS.java.
 * The caller MUST determine whether banking rules permit balance updates:
 * legacy excludes deposits, RETESP, VERESP and nonstandard counter operations.
 * An unknown classification is rejected BEFORE the financial write begins.
 * Caller invokes this via BkevePostingAndBalanceWrite on its transaction connection.
 */
final class LegacyBkeveIndicativeBalanceUpdate implements BkeveIndicativeBalanceUpdate {
    private static final String DEBIT = "update bkcom set sin = sin - ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ? ";
    private static final String CREDIT = "update bkcom set sin = sin + ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ? ";
    private static final String DEBIT_SYN = "update syn_bkcom set sin = sin - ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ? ";
    private static final String CREDIT_SYN = "update syn_bkcom set sin = sin + ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ? ";
    private static final String NIGHT = "insert into bksin (age, dev, ncp, suf, mon, orig, flag) values (?, ?, ?, ?, ?, ?, ?) ";

    record Leg(String age, String dev, String ncp, String suf, String clc, BigDecimal amount) {
        Leg {
            for (String value : new String[] {age, dev, ncp, suf, clc}) {
                if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing bank account enrichment");
            }
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Positive leg amount required");
        }
    }

    private final Leg debit;
    private final Leg credit;
    private final boolean updateBalances;

    /** updateBalances is determined by a bank-validated operation classification, not inferred. */
    LegacyBkeveIndicativeBalanceUpdate(Leg debit, Leg credit, Boolean updateBalances) {
        this.debit = Objects.requireNonNull(debit, "debit");
        this.credit = Objects.requireNonNull(credit, "credit");
        if (updateBalances == null) throw new IllegalArgumentException("Bank operation classification required");
        this.updateBalances = updateBalances;
    }

    @Override public void update(Connection connection, String eventTable, String eventNumber) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (!"BKEVE".equals(eventTable) && !"BKEVE_EOD".equals(eventTable))
            throw new IllegalArgumentException("Unsupported event table");
        if (eventNumber == null || !eventNumber.matches("[0-9]{6}"))
            throw new IllegalArgumentException("Invalid event number");
        if (connection.getAutoCommit()) throw new IllegalStateException("Caller-owned JDBC transaction required");
        if (!updateBalances) return;
        if ("BKEVE".equals(eventTable)) {
            // Legacy order: credit, then debit.
            updateAccount(connection, CREDIT, credit);
            updateAccount(connection, DEBIT, debit);
        } else {
            // Legacy night mode: bksin debit(-), bksin credit(+), SYN credit, SYN debit.
            insertNight(connection, debit, debit.amount().negate());
            insertNight(connection, credit, credit.amount());
            updateAccount(connection, CREDIT_SYN, credit);
            updateAccount(connection, DEBIT_SYN, debit);
        }
    }

    private static void updateAccount(Connection c, String sql, Leg leg) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setBigDecimal(1, leg.amount());
            s.setString(2, leg.age());
            s.setString(3, leg.ncp());
            s.setString(4, leg.clc());
            if (s.executeUpdate() != 1) throw new SQLException("Indicative account update did not affect exactly one row");
        }
    }

    private static void insertNight(Connection c, Leg leg, BigDecimal signedAmount) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(NIGHT)) {
            s.setString(1, leg.age());
            s.setString(2, leg.dev());
            s.setString(3, leg.ncp());
            s.setString(4, leg.suf());
            s.setBigDecimal(5, signedAmount);
            s.setString(6, "WEB");
            s.setString(7, "O");
            if (s.executeUpdate() != 1) throw new SQLException("Night balance movement did not affect exactly one row");
        }
    }
}
