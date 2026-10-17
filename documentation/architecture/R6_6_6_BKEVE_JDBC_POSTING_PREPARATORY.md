# R6.6.6 — BKEVE JDBC posting boundary (preparatory)

**Status: PREPARATORY / BLOCKED_BANK_EVIDENCE — DO NOT ENABLE FINANCIAL POSTING.**

Repository: `SIXGEN-Solutions/regional-core-banking-api`.
Source branch: `feat/payment-orchestration-worflow`.
Base for this local patch: `dc72d4fb36b08eb59dc7439a5ae235b345e30065`.
Earlier requested historical revision: `c96fab3abefdd8e999ddc3d841cf7e323519df68` (three commits behind current HEAD).

## R6.6.5 closure assessment

The local JVM `ReentrantLock` serializes calls through `JdbcBkeveTransactionCoordinator`
only within one Spring Boot JVM. The `LockedAllocator` has no authorized Informix
implementation. `PaymentExecutionBankingContextResolver` still uses `MAX(eve)` plus
an existence check without database reservation. Other banking producers can race.
**Result: R6.6.5 local coordination validated; banking allocation NOT closed.**

## Scope implemented in R6.6.6

`BkeveJdbcPostingAdapter` is a non-Spring-wired infrastructure component that:

- accepts ONLY `BKEVE` or `BKEVE_EOD` as an explicitly chosen table;
- uses a **bank-approved injected SQL provider** with NO built-in SQL;
- requires a caller-owned JDBC transaction with autocommit disabled;
- consumes the existing 233-column `BkeveJdbcParameterMapper.Parameters`;
- binds 233 ordered parameters and requires exactly one inserted row;
- neither opens another connection nor commits, rolls back, nor retries;
- leaves the existing `JdbcPaymentExecutionBankingAdapter` fail-closed.

The unit tests use JDBC mocks to characterize day/night selection, 233 binds,
reject missing approved SQL, and verify no writer-owned commit or retry.
**These are NOT live Informix integration tests.** No mock SQL proves that the
physical 233-column mapping is correct, or that a real banking transaction succeeds.

## Blocking banking evidence before real INSERT or Informix integration tests

- Bank-approved exact INSERT for BKEVE/BKEVE_EOD including 233 physical columns,
  types, NULL/CHAR padding and JDBC binding semantics. The CSV is LEGACY/REFERENCE_ONLY.
- Verified full authoritative inputs to all 233 positions and account enrichment.
- Approved atomic EVE allocation with coexistence of other Amplitude producers,
  plus day/night switching and counter rollover rules.
- Approved indicative-balance update in the same transaction as BKEVE.
- Authoritative payment/idempotency lookup, unknown-commit recovery and bank reference.
- An authorized disposable Informix test environment, approved fixtures, rollback
  semantics and a tested negative/positive banking scenario.

No new OpenAPI endpoint, schema, security setting, SQL statement, Informix table,
column, migration or SIXPAY modification is included in this preparatory patch.

## User-run gates

```bash
./mvnw -Dtest=BkeveJdbcPostingAdapterTest test
./mvnw clean verify
 git diff --check
 git status --short
```
