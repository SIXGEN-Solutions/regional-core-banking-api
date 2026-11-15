package com.regional.corebanking.payment.infrastructure.amplitude;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * JDBC commands transcribed from RequeteSQLCBS(2).java.
 * Choice to mutate balances must be taken by an approved policy upstream.
 * No local commit, retry, or connection ownership.
 */
final class BkeveIndicativeBalanceStatements {
    enum Mode { NORMAL, NIGHT }
    record Account(String age, String dev, String ncp, String suf, String clc) {}

    private static final String CREDIT =
        "update bkcom set sin = sin + ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ?";
    private static final String DEBIT =
        "update bkcom set sin = sin - ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ?";
    private static final String CREDIT_SYN =
        "update syn_bkcom set sin = sin + ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ?";
    private static final String DEBIT_SYN =
        "update syn_bkcom set sin = sin - ? where trim(age) = ? and trim(ncp) = ? and trim(clc) = ?";
    private static final String NIGHT =
        "insert into bksin (age, dev, ncp, suf, mon, orig, flag) values (?, ?, ?, ?, ?, ?, ?)";

    void execute(Connection c, Mode mode, Account debtor, Account creditor,
                 BigDecimal debit, BigDecimal credit, boolean bankApprovedToUpdate)
            throws SQLException {
        Objects.requireNonNull(c, "connection");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        positive(debit, "debit");
        positive(credit, "credit");
        if (!bankApprovedToUpdate)
            throw new IllegalStateException("Balance mutation requires approved operation/nature policy");
        if (c.getAutoCommit())
            throw new IllegalStateException("Balance updates require a caller-owned transaction");
        if (mode == Mode.NORMAL) {
            update(c, CREDIT, credit, creditor);
            update(c, DEBIT, debit, debtor);
        } else {
            insertNight(c, debtor, debit.negate());
            insertNight(c, creditor, credit);
            update(c, CREDIT_SYN, credit, creditor);
            update(c, DEBIT_SYN, debit, debtor);
        }
    }

    private static void positive(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException(field + " must be positive");
    }

    private static void update(Connection c, String sql, BigDecimal amount, Account a)
            throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setBigDecimal(1, amount);
            p.setString(2, a.age());
            p.setString(3, a.ncp());
            p.setString(4, a.clc());
            if (p.executeUpdate() != 1)
                throw new SQLException("Indicative balance UPDATE did not affect exactly one account");
        }
    }

    private static void insertNight(Connection c, Account a, BigDecimal signed)
            throws SQLException {
        try (PreparedStatement p = c.prepareStatement(NIGHT)) {
            p.setString(1, a.age());
            p.setString(2, a.dev());
            p.setString(3, a.ncp());
            p.setString(4, a.suf());
            p.setBigDecimal(5, signed);
            p.setString(6, "WEB");
            p.setString(7, "O");
            if (p.executeUpdate() != 1)
                throw new SQLException("Night indicative balance INSERT did not affect exactly one row");
        }
    }
}
