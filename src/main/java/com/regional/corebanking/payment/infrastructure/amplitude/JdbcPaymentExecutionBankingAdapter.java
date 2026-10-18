package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.application.port.out.PaymentEventNumberReservationPort;
import com.regional.corebanking.payment.application.port.out.PaymentBankEventTrackingPort;
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
    private final BkeveIndicativeBalanceUpdate indicativeBalances;
    private final PaymentEventNumberReservationPort reservations;
    private final PaymentBankEventTrackingPort tracking;

    JdbcPaymentExecutionBankingAdapter(DataSource ds, PaymentExecutionSqlDialect q, String fic) {
        this(ds, q, fic, BkeveIndicativeBalanceUpdate.unconfigured(), null, null);
    }

    JdbcPaymentExecutionBankingAdapter(DataSource ds, PaymentExecutionSqlDialect q,
                                       String fic, BkeveIndicativeBalanceUpdate balances) {
        this(ds, q, fic, balances, null, null);
    }

    JdbcPaymentExecutionBankingAdapter(DataSource ds, PaymentExecutionSqlDialect q,
                                       String fic, BkeveIndicativeBalanceUpdate balances,
                                       PaymentEventNumberReservationPort reservations) {
        this(ds, q, fic, balances, reservations, null);
    }

    JdbcPaymentExecutionBankingAdapter(DataSource ds, PaymentExecutionSqlDialect q,
                                       String fic, BkeveIndicativeBalanceUpdate balances,
                                       PaymentEventNumberReservationPort reservations,
                                       PaymentBankEventTrackingPort tracking) {
        this.reservations = reservations;
        this.tracking = tracking;
        this.ds = Objects.requireNonNull(ds, "ds");
        this.q = Objects.requireNonNull(q, "q");
        if (fic == null || fic.isBlank()) throw new IllegalArgumentException("financialInstitutionCode is required");
        this.fic = fic.strip();
        this.indicativeBalances = Objects.requireNonNull(balances, "balances");
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
            if (e.debitAmount() == null || e.debitAmount().signum() <= 0)
                throw new IllegalArgumentException("providerEvent.debitAmount must be positive");
            // Only the debit leg impacts the customer's available funds check.
            // Credit amount is independently supplied and retained in the command.
            if (s.compareTo(e.debitAmount()) < 0)
                return reject(c.paymentReference(), av, da, fail(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT, "INSUFFICIENT_FUNDS"), "INSUFFICIENT_FUNDS");
            // Controls have passed. Allocate, map and post on one writable
            // JDBC connection; do not reuse the read-only precheck connection.
            // The bank update policy is fail-closed until configured from approved evidence.
            return executeFinancialTransaction(k, c, e, ctx, av, da);
        } catch (SQLException z) {
            throw un("Unable to execute Amplitude pre-execution controls: " + z.getMessage());
        }
    }

    private PaymentExecutionResult executeFinancialTransaction(String reservationKey, PaymentExecutionCommand command,
            PaymentProviderEvent event, PaymentExecutionBankingContext observed,
            PaymentExecutionCheck accountValid, PaymentExecutionCheck debitAllowed) {
        var mapper = new BkeveJdbcParameterMapper();
        var posting = new BkeveJdbcPostingAdapter(mapper);
        // Balance updates follow the validated bank operation/nature policy.
        // Explicit non-default constructor injection can still override for tests.
        var writer = new BkevePostingAndBalanceWrite(posting,
                tracking != null ? new BkeveExecutionBalanceUpdate(event, observed) : indicativeBalances);
        var assembly = new BkeveTransactionWriteAssembly(
                new BkeveBankEnrichmentService(), mapper, writer);
        var coordinator = new JdbcBkeveTransactionCoordinator(ds,
                (connection, operation, table, accountingDate) -> {
                    // This allocator executes while the OPE-specific JVM mutex
                    // is held, on the same transaction connection as INSERT.
                    var actual = new PaymentExecutionBankingContextResolver(q)
                            .resolve(connection, operation, event.currency());
                    if (!actual.accountingDate().equals(accountingDate)
                            || !actual.eventTable().equals(table))
                        throw new SQLException("Accounting period changed before financial posting");
                    if (reservations == null)
                        throw new SQLException("PostgreSQL EVE reservation is not configured");
                    // Atomic durable PostgreSQL reservation BEFORE BKEVE INSERT.
                    // A reserved EVE is not evidence of financial execution.
                    return reservations.reserve(fic, reservationKey, operation, actual.eventNumber());
                });
        return coordinator.allocateAndWrite(event.operationCode(), observed.eventTable(),
                observed.accountingDate(), (connection, eve) -> {
                    var bankContext = new PaymentExecutionBankingContext(eve,
                            observed.accountingDate(), observed.nightMode(),
                            observed.eventTable(), observed.debitValueDate(),
                            observed.creditValueDate(), observed.currency());
                    // Any unresolved BKEVE column fails before INSERT.
                    assembly.execute(connection, eve, event, bankContext, Map.of());
                    return new PaymentExecutionResult(command.paymentReference(),
                            PaymentExecutionOutcome.COMPLETED,
                            List.of(accountValid, debitAllowed,
                                pass(PaymentExecutionCheckType.AVAILABLE_FUNDS_SUFFICIENT)),
                            event.operationCode() + "/" + eve, null,
                            OffsetDateTime.now(ZoneOffset.UTC));
                });
    }

    public Optional<PaymentExecutionResult> findByPaymentReference(String f, String p) {
        institution(f);
        return tracking == null ? Optional.empty() : tracking.findByPaymentReference(f, p)
                .flatMap(this::verifiedBankEvent);
    }

    public Optional<PaymentExecutionResult> findByIdempotencyKey(String f, String k) {
        institution(f);
        return tracking == null ? Optional.empty() : tracking.findByIdempotencyKey(f, k)
                .flatMap(this::verifiedBankEvent);
    }

    private Optional<PaymentExecutionResult> verifiedBankEvent(PaymentBankEventTrackingPort.TrackedEvent event) {
        // A PostgreSQL reservation is NEVER proof of banking execution.
        // The result is COMPLETED only after a real banking BKEVE/BKEVE_EOD lookup.
        try (Connection connection = open()) {
            long count = 0;
            for (String table : List.of("BKEVE", "BKEVE_EOD")) {
                try (PreparedStatement stmt = connection.prepareStatement(q.countEventNumber(table))) {
                    stmt.setString(1, event.eventNumber());
                    stmt.setString(2, event.operationCode());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw un("Bank event verification returned no count");
                        count += rs.getLong("nbr");
                    }
                }
            }
            if (count == 0) return Optional.empty(); // Absence is not financial rejection.
            if (count != 1) throw un("Ambiguous banking event; manual reconciliation required");
            return Optional.of(new PaymentExecutionResult(event.paymentReference(),
                    PaymentExecutionOutcome.COMPLETED, List.of(),
                    event.operationCode() + "/" + event.eventNumber(), null,
                    OffsetDateTime.now(ZoneOffset.UTC)));
        } catch (SQLException failure) {
            var ex = un("Bank event recovery lookup failed; outcome remains UNKNOWN");
            ex.initCause(failure);
            throw ex;
        }
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
