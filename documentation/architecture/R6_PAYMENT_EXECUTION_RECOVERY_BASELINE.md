# R6 — Payment Execution / Recovery baseline

## Scope

R6 implements the Regional application boundary for the three already-approved
Payment Execution operations:

- `POST /api/v1/payment-events`;
- `GET /api/v1/payment-events/{paymentReference}`;
- `GET /api/v1/payment-events/idempotency/{idempotencyKey}`.

No OpenAPI endpoint or schema is added by this increment.

## Safety model

The submitted `PaymentEventEnvelope` is a financial command. Application code calls
the banking execution port once. It never performs an automatic financial retry.

`UNKNOWN` is a first-class contract outcome. A caller must recover through the
authoritative lookup by `paymentReference` or the original `Idempotency-Key` before
considering any retry. Recovery methods never call the execute method.

The banking boundary owns authoritative financial idempotency. A same-key replay or
same-key/different-request conflict must be resolved by the eventual approved banking
adapter without duplicating the financial action.

## Context

The approved Regional V1 contract carries the execution context inside
`PaymentProviderEvent`: operation code, event number, accounting date, technical user,
debtor/creditor account references and night-mode flag.

Historical SIXPAY reference endpoints for allocating event number, reading accounting
date and reading night mode are not part of the approved Regional V1 contract.
R6 therefore does not expose or invent them.

## Physical Amplitude / Informix adapter

The current repository evidence does not define an approved La Régionale SQL query,
stored procedure, transaction boundary, execution-check mapping, bank-reference
allocation mechanism or authoritative lookup query for Payment Event execution.

The infrastructure adapter therefore fails closed with HTTP 503. It does not simulate
a successful debit, return a fabricated bank reference, or write directly to guessed
`bkeve` / `bkmvti` tables.

Replacing the fail-closed adapter requires approved La Régionale banking evidence for:

1. execution entry point (procedure/service/JDBC contract);
2. atomic event + two-entry transaction semantics;
3. all eight execution-time checks and rejection mapping;
4. idempotency storage/lookup semantics;
5. authoritative lookup by payment reference and original idempotency key;
6. bank-reference source and success criterion;
7. uncertain-outcome behavior and transaction recovery.

## Compatibility

SIXPAY material remains `REFERENCE_ONLY`. It supports the no-blind-replay and
authoritative-recovery semantics but is not used as the Regional Java domain model.

## Status

`INCOMPLETE_CONTEXT` for physical banking execution.

The Regional HTTP/application/domain boundary is implementable and testable now.
Production financial execution remains blocked on approved La Régionale evidence.
