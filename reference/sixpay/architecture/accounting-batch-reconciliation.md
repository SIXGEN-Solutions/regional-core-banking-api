# Lot 5.6.4 — Accounting batch tracking, idempotence and reconciliation

## Objective

Close the Payment -> Accounting integration loop without making TFJ or SFTP a
SIXPAY responsibility.

The batch business status remains intentionally small:

- `COMPLETED`
- `NOT_COMPLETED`

Transport/submission state is persisted separately:

- `READY`
- `SUBMITTING`
- `SUBMITTED`
- `OUTCOME_UNKNOWN`
- `COMPLETED`
- `REJECTED`
- `RECONCILIATION_REQUIRED`

This separation prevents transport uncertainty from leaking into the Accounting
business model.

## Crash-safe submission protocol

Before the external POST:

1. load the persisted Accounting batch;
2. create/load its tracking record;
3. persist `SUBMITTING`;
4. issue `POST /v1/accounting/batches`.

If the process crashes after step 3, a restart never POSTs blindly. Any state
other than `READY` enters reconciliation first.

This deliberately prefers a temporarily stuck batch over a duplicate accounting
side effect.

## Unknown-outcome protocol

For timeout, 429, 5xx or unusable POST response:

1. persist `OUTCOME_UNKNOWN`;
2. retain the original batch ID;
3. retain the original idempotency key;
4. never create a replacement batch;
5. never auto-retry POST;
6. lookup by idempotency key first;
7. fallback to lookup by SIXPAY batch ID;
8. if still absent, retain the uncertain state and increment the reconciliation
   attempt counter.

Absence from lookup does not prove the original POST failed. Re-submission needs
a later explicit operational policy.

## Provider-result reconciliation

A valid provider response updates atomically:

- `AccountingBatch.status`;
- each Accounting item status;
- provider batch reference;
- provider item references;
- rejection codes;
- tracking state;
- reconciliation timestamps/counters.

Provider references are stored in dedicated tracking persistence and do not
change the canonical Payment facts copied into the batch.

## Idempotence

There are three independent protections:

1. batch deterministic SHA-256 idempotency key;
2. DB uniqueness on `accounting_batches.idempotency_key`;
3. DB uniqueness on `accounting_batch_items.payment_id`.

Submission additionally uses the same idempotency key in the configured HTTP
header.

## Persistence update rule

Existing Accounting items are updated in place during reconciliation. They are
not cleared/reinserted, avoiding conflicts with the global unique constraint on
`payment_id`.

## Still outside SIXPAY

- TFJ physical format;
- chart-of-accounts codes;
- debit/credit rules;
- file naming;
- SFTP;
- technical file ACK/NACK.
