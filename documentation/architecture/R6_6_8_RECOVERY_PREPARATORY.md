# R6.6.8 — Recovery read-only / non-double-postage (preparatory)

**Status: PREPARATORY / BLOCKED_BANK_EVIDENCE.** No new SQL, endpoint, schema or financial posting activation.

Repository `SIXGEN-Solutions/regional-core-banking-api`; branch `feat/payment-orchestration-worflow`;
patch baseline `7ffb9a3ec6c62d14880ceffb444e4c1fbba4eeac`.

## R6.6.7 closure check
R6.6.7 composes BKEVE insert and indicative balances on the same caller-owned JDBC
connection, and Mockito tests cover rollback. The actual bank balance updater remains
unconfigured; no approved day/night SQL or real Informix rollback test exists. Thus
R6.6.7's transaction orchestration is preparatory, not production-closed.

## R6.6.8 changes
`PaymentExecutionRecoveryCoordinator` provides read-only recovery coordination for
both already-approved lookup endpoints. The existing application service delegates
its recovery reads to the coordinator. If the local result is `UNKNOWN`, a banking
lookup is attempted. If the bank cannot resolve it, technical UNKNOWN is preserved.
If neither source provides a result, the existing not-found exception is retained;
**absence is NOT evidence that the operation never posted**. The coordinator never
invokes financial execution, never retries and does not fabricate bank references.
For an authoritative `COMPLETED` result, a nonblank bank reference is required.
Payment reference equality is verified when that correlation is available.

The existing PostgreSQL `regional_payment_execution` idempotency claim is preserved
across failure; `executeIdempotent` prevents a second financial execution for the
same unresolved idempotency key. This change does not invent a bank lookup algorithm,
postgreSQL migrations or a durable OPE/EVE/DCO mapping.

## Limitations / required approvals
- An authoritative Regional `paymentReference -> OPE/EVE/DCO -> BKEVE` correlation
  write must be designed and validated, with crash/commit uncertainty semantics.
  Existing schema has nullable `event_number` and `accounting_date`; they are
  not populated by the unconfigured posting path.
- Bank-authoritative lookup SQL/procedure, unique matching criteria, visibility,
  reversal/rejection interpretation and proof of *no effect* remain to be approved.
- The current Informix adapter lookup methods return `Optional.empty()` and posting
  is fail-closed. Thus real UNKNOWN outcomes are **not resolved** by this patch.
- `UNKNOWN` with missing bank record may NEVER authorize another posting.
- Tests are mocked, not real Informix / PostgreSQL failure-injection tests.
- R6.6.5 EVE allocation, R6.6.6 physical BKEVE INSERT and R6.6.7 balances remain
  banking-evidence blockers before end-to-end production enablement.

## Local user-run validation

```sh
./mvnw -Dtest=PaymentExecutionRecoveryCoordinatorTest,PaymentExecutionServiceTest test
./mvnw clean verify
git diff --check
git status --short
```
