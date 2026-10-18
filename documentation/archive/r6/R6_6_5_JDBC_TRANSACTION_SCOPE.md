# R6.6.5 — JDBC transaction-scoped EVE coordination (preparatory)

Status: **IMPLEMENTATION_PREPARATORY / BLOCKED_BANK_EVIDENCE**, not production banking allocation.

- Source: `SIXGEN-Solutions/regional-core-banking-api`, branch `feat/payment-orchestration-worflow`, SHA `7fcec802a731f5f69f9d615d9e321fff44944696`.
- `JdbcBkeveTransactionCoordinator` ensures the injected bank-approved allocator and BKEVE financial writer share **the same JDBC connection**, one explicit transaction and one commit attempt.
- It handles validation and failure without retries. A failed commit is explicitly uncertain and requires authoritative recovery.
- The `LockedAllocator` is deliberately not supplied: no approved SQL/procedure, locking key, isolation semantics, physical schema or uniqueness constraint was evidenced.
- Multiple JVMs can be supported **only after** the allocator is backed by bank-authoritative inter-instance database exclusion held through the write and commit. The coordinator alone does not supply exclusion.
- The existing `PaymentExecutionBankingContextResolver` still uses unsafe `MAX(eve)` to populate a provisional context. That value MUST NOT be used for a financial write. Production wiring remains disabled.
- The R6.6.4 enrichment and R6.6.3 position mapper are not given invented default values.
- Postage BKEVE, its authorized write SQL, rollback/commit semantics and idempotency recovery remain separate bank-approval gates.

Required bank evidence: transaction-safe BKOPE/SYN_BKOPE allocation source and locking, uniqueness scope/constraint, rollover, night/day transition, physical BKEVE INSERT mapping, and authoritative unknown-outcome recovery.

Tests (user execution): `./mvnw -Dtest=JdbcBkeveTransactionCoordinatorTest test`, then `./mvnw clean verify`.
