package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/**
 * Assembles banking context enrichment, 233-position mapping,
 * BKEVE INSERT, and indicative balance update in one caller-owned transaction.
 * This does NOT allocate EVE, authorize SQL, commit, retry or activate posting.
 * Must be invoked within JdbcBkeveTransactionCoordinator.FinancialWrite only.
 */
final class BkeveTransactionWriteAssembly {
    private final BkeveBankEnrichmentService enrichment;
    private final BkeveJdbcParameterMapper mapper;
    private final BkevePostingAndBalanceWrite writer;

    BkeveTransactionWriteAssembly(BkeveBankEnrichmentService enrichment,
                                  BkeveJdbcParameterMapper mapper,
                                  BkevePostingAndBalanceWrite writer) {
        this.enrichment = Objects.requireNonNull(enrichment, "enrichment");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    void execute(Connection connection, String allocatedEventNumber,
                 PaymentProviderEvent event, PaymentExecutionBankingContext bankContext,
                 Map<String, ?> approvedBankValues) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(bankContext, "bankContext");
        Objects.requireNonNull(approvedBankValues, "approvedBankValues");
        if (allocatedEventNumber == null || !allocatedEventNumber.matches("[0-9]{6}")
                || "000000".equals(allocatedEventNumber)) {
            throw new IllegalArgumentException("Valid transaction-allocated EVE required");
        }
        if (!allocatedEventNumber.equals(bankContext.eventNumber())) {
            throw new IllegalStateException("Banking context EVE differs from transaction-allocated EVE");
        }
        if (!"BKEVE".equals(bankContext.eventTable())
                && !"BKEVE_EOD".equals(bankContext.eventTable())) {
            throw new IllegalArgumentException("Unsupported banking context event table");
        }
        // Validate ALL values before beginning the financial write. Unknown
        // physical mappings are never inferred from historic default values.
        var enriched = enrichment.enrich(bankContext, approvedBankValues);
        var parameters = mapper.map(event, enriched);
        writer.execute(connection, bankContext.eventTable(), allocatedEventNumber, parameters);
    }
}
