package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/** processPOST indicative balance rules; same transaction as BKEVE INSERT. */
final class BkeveExecutionBalanceUpdate implements BkeveIndicativeBalanceUpdate {
    private static final Set<String> DEPOSITS = Set.of("001", "011");
    private static final Set<String> EXEMPT_NATURES = Set.of("RETESP", "VERESP");
    // getListOpeguichetnonstandard() is empty, confirmed by La Regionale.
    private final PaymentProviderEvent event;
    private final PaymentExecutionBankingContext context;
    private final BkeveIndicativeBalanceStatements statements = new BkeveIndicativeBalanceStatements();
    private final boolean enabled;
    private BkeveIndicativeBalanceStatements.Account debtor;
    private BkeveIndicativeBalanceStatements.Account creditor;

    BkeveExecutionBalanceUpdate(PaymentProviderEvent event, PaymentExecutionBankingContext context) {
        this.event = java.util.Objects.requireNonNull(event);
        this.context = java.util.Objects.requireNonNull(context);
        this.enabled = !DEPOSITS.contains(event.operationCode()) && (event.nature() == null || !EXEMPT_NATURES.contains(event.nature()));
    }
    @Override
    public void validateBeforeWrite(String table, String eve) {
        if (!table.equals(context.eventTable())) throw new IllegalArgumentException("Bank event table mismatch");
        if (!enabled) return;
        if (event.debitAmount() == null || event.debitAmount().signum() <= 0
                || event.creditAmount() == null || event.creditAmount().signum() <= 0)
            throw new IllegalArgumentException("Positive BKEVE leg amounts required");
        debtor = account(event.debtorAccountReference(), "DEV1", "SUF1");
        creditor = account(event.creditorAccountReference(), "DEV2", "SUF2");
    }
    private BkeveIndicativeBalanceStatements.Account account(String reference, String devKey, String sufKey) {
        if (reference == null) throw new IllegalArgumentException("Missing BKEVE account reference");
        String[] p = reference.split("-", -1);
        if (p.length != 3 || p[0].isBlank() || p[1].isBlank() || p[2].isBlank())
            throw new IllegalArgumentException("Invalid age-ncp-clc account reference");
        Map<String,Object> v = event.bkeveEvent().values();
        // Night mode requires physical banking account details, never derive from XAF.
        String dev = bankValue(v.get(devKey));
        String suf = bankValue(v.get(sufKey));
        if (context.nightMode() && (dev == null || suf == null))
            throw new IllegalArgumentException("Night balance requires bank DEV/SUF account details");
        return new BkeveIndicativeBalanceStatements.Account(p[0], dev, p[1], suf, p[2]);
    }
    private static String bankValue(Object value) {
        return value == null ? null : value.toString();
    }
    @Override
    public void update(Connection connection, String table, String eve) throws SQLException {
        if (!enabled) return;
        if (debtor == null || creditor == null)
            throw new IllegalStateException("Balance preflight not executed");
        statements.execute(connection,
                context.nightMode() ? BkeveIndicativeBalanceStatements.Mode.NIGHT
                                    : BkeveIndicativeBalanceStatements.Mode.NORMAL,
                debtor, creditor, event.debitAmount(), event.creditAmount(), true);
    }
}
