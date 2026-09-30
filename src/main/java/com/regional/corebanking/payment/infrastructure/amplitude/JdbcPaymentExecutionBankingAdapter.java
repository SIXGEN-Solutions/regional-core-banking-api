package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

final class JdbcPaymentExecutionBankingAdapter implements PaymentExecutionBankingPort {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("ddMMyyyy");
    private final DataSource ds;
    private final PaymentExecutionSqlDialect q;
    private final String fic;

    JdbcPaymentExecutionBankingAdapter(DataSource ds, PaymentExecutionSqlDialect q, String fic) {
        this.ds = ds;
        this.q = q;
        if (fic == null || fic.isBlank()) throw new IllegalArgumentException("financialInstitutionCode is required");
        this.fic = fic.strip();
    }

    private static boolean row(PreparedStatement s) throws SQLException {
        try (ResultSet r = s.executeQuery()) {
            return r.next();
        }
    }

    private static PaymentExecutionCheck pass(PaymentExecutionCheckType t) {
        return new PaymentExecutionCheck(t, PaymentExecutionCheckResult.PASS, null);
    }

    private static PaymentExecutionCheck fail(PaymentExecutionCheckType t, String r) {
        return new PaymentExecutionCheck(t, PaymentExecutionCheckResult.FAIL, r);
    }

    private static PaymentExecutionCheck unk(PaymentExecutionCheckType t) {
        return new PaymentExecutionCheck(t, PaymentExecutionCheckResult.UNKNOWN, null);
    }

    private static PaymentExecutionResult reject(String p, PaymentExecutionCheck a, PaymentExecutionCheck b, PaymentExecutionCheck c, String r) {
        return new PaymentExecutionResult(p, PaymentExecutionOutcome.REJECTED, List.of(a, b, c), null, r, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static PaymentExecutionUnavailableException un(String m) {
        return new PaymentExecutionUnavailableException(m);
    }

    public PaymentExecutionResult execute(String f, String k, PaymentExecutionCommand c) {
        institution(f);
        var e = c.providerEvent();
        var a = Parts.parse(e.debtorAccountReference());
        try (Connection x = open()) {
            var ac = account(x, a);
            if (ac == null)
                return reject(c.paymentReference(), fail(PaymentExecutionCheckType.ACCOUNT_VALID, "ACCOUNT_INVALID"), unk(PaymentExecutionCheckType.DEBIT_ALLOWED), unk(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT), "ACCOUNT_INVALID");
            var av = pass(PaymentExecutionCheckType.ACCOUNT_VALID);
            PaymentExecutionBankingContext ctx = new PaymentExecutionBankingContextResolver(q).resolve(x, e.operationCode(), ac.dev);
            LocalDate d = ctx.accountingDate();
            if (opCli(x, d, ac.cli) || opNcp(x, d, a) || opOpe(x, e.operationCode()))
                return reject(c.paymentReference(), av, fail(PaymentExecutionCheckType.DEBIT_ALLOWED, "DEBIT_OPPOSED"), unk(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT), "DEBIT_OPPOSED");
            var da = pass(PaymentExecutionCheckType.DEBIT_ALLOWED);
            BigDecimal s = funds(x, a, ac.dev);
            if (s == null) throw un("Available funds could not be determined from Core Banking");
            if (e.amount() == null) throw new IllegalArgumentException("providerEvent.amount is required");
            if (s.compareTo(e.amount()) < 0)
                return reject(c.paymentReference(), av, da, fail(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT, "INSUFFICIENT_FUNDS"), "INSUFFICIENT_FUNDS");
            throw un("R6.5 authoritative BKEVE context calculated: eventNumber=" + ctx.eventNumber() + ", accountingDate=" + ctx.accountingDate() + ", nightMode=" + ctx.nightMode() + ", eventTable=" + ctx.eventTable() + ", debitValueDate=" + ctx.debitValueDate() + ", creditValueDate=" + ctx.creditValueDate() + ", currency=" + ctx.currency() + "; BKEVE financial execution is not implemented in this lot");
        } catch (SQLException z) {
            throw un("Unable to execute Amplitude pre-execution controls: " + z.getMessage());
        }
    }

    public Optional<PaymentExecutionResult> findByPaymentReference(String f, String p) {
        institution(f);
        return Optional.empty();
    }

    public Optional<PaymentExecutionResult> findByIdempotencyKey(String f, String k) {
        institution(f);
        return Optional.empty();
    }

    private Connection open() throws SQLException {
        Connection c = ds.getConnection();
        boolean ok = false;
        try {
            c.setReadOnly(true);
            q.prepareConnection(c);
            ok = true;
            return c;
        } finally {
            if (!ok) c.close();
        }
    }

    private Ac account(Connection c, Parts a) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findActiveAccount())) {
            s.setString(1, a.age);
            s.setString(2, a.ncp);
            s.setString(3, a.clc);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Ac(r.getString("cli").trim(), r.getString("dev").trim()) : null;
            }
        }
    }

    private boolean opCli(Connection c, LocalDate d, String cli) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findCustomerOpposition())) {
            s.setDate(1, java.sql.Date.valueOf(d));
            s.setString(2, cli);
            return row(s);
        }
    }

    private boolean opNcp(Connection c, LocalDate d, Parts a) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findAccountOpposition())) {
            s.setDate(1, java.sql.Date.valueOf(d));
            s.setString(2, a.age);
            s.setString(3, a.ncp);
            return row(s);
        }
    }

    private boolean opOpe(Connection c, String o) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findOperationOpposition())) {
            s.setString(1, o);
            return row(s);
        }
    }

    private BigDecimal funds(Connection c, Parts a, String dev) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findAvailableFunds())) {
            s.setString(1, a.age);
            s.setString(2, a.ncp);
            s.setString(3, dev);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getBigDecimal("solde") : null;
            }
        }
    }

    private LocalDate date(Connection c) throws SQLException {
        String v = val(c, q.findAccountingDatePrimary(), "mnt2");
        if (v == null || v.equalsIgnoreCase("0") || v.strip().length() == 1)
            v = val(c, q.findAccountingDateFallback(), "mnt1");
        if (v == null) throw un("Core Banking accounting date is null");
        v = v.strip();
        if (v.length() == 7) v = "0" + v;
        try {
            return LocalDate.parse(v, D);
        } catch (DateTimeParseException e) {
            throw un("Core Banking accounting date is invalid");
        }
    }

    private String val(Connection c, String sql, String col) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, "001");
            s.setString(2, "00099");
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(col) : null;
            }
        }
    }

    private boolean night(Connection c) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(q.findNightMode()); ResultSet r = s.executeQuery()) {
            return r.next() && r.getInt("mnt4") == 1;
        }
    }

    private void institution(String f) {
        if (!fic.equals(f)) throw new IllegalArgumentException("Unsupported financialInstitutionCode");
    }

    private record Ac(String cli, String dev) {
    }

    private record Parts(String age, String ncp, String clc) {
        static Parts parse(String v) {
            if (v == null || v.isBlank()) throw new IllegalArgumentException("debtorAccountReference is required");
            String[] p = v.strip().split("-", -1);
            if (p.length != 3 || p[0].isBlank() || p[1].isBlank() || p[2].isBlank())
                throw new IllegalArgumentException("Unsupported debtorAccountReference format; expected age-ncp-clc");
            return new Parts(p[0], p[1], p[2]);
        }
    }
}
