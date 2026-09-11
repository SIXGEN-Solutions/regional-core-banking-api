# Lot 5.6.3 — Accounting/TFJ API client

## Boundary

SIXPAY sends the canonical Accounting batch created in Lots 5.6.1/5.6.2 to
the downstream Accounting/TFJ API. SIXPAY does not generate TFJ files and does
not implement SFTP.

## Operations

- `POST /v1/accounting/batches`
- `GET /v1/accounting/batches/{batchId}`
- `GET /v1/accounting/batches/by-idempotency-key/{idempotencyKey}`

The external contract remains provisional and configuration-driven.

## Security baseline

- HTTPS only;
- mTLS through a Spring Boot SSL bundle;
- OAuth2 client credentials through a configured client registration;
- correlation ID and request ID propagated on every request;
- no secret or access token in operational payloads.

## Submission semantics

Batch submission is an external side effect.

The client never retries `POST` automatically.

A transport timeout, HTTP 429 or HTTP 5xx after request emission is classified
as `AccountingSubmissionOutcomeUnknownException`. The caller must resolve the
result using the existing lookup by idempotency key before deciding any further
action.

HTTP 409 and 422 are deterministic provider rejections. HTTP 401/403 are
authentication/authorization failures.

## Lookup semantics

Lookup is read-only.

- 200 -> authoritative provider result;
- 404 -> no result found;
- 401/403 -> authentication failure;
- 429/5xx or transport failure -> provider unavailable;
- malformed 200 -> invalid provider response.

No TFJ format, accounting codes, debit/credit rules, file names or SFTP
configuration is introduced into SIXPAY.
