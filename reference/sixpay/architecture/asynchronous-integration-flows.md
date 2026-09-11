# Asynchronous Integration Flows

## 1. Scope

This document covers interactions whose completion is decoupled from the initiating HTTP transaction.

Asynchronous delivery does not remove the need for a contract, idempotency, ownership, error classification, observability and replay governance.

## 2. Flow ASY-01 — Payment projects to Observed Customer

```text
Payment transaction
  -> Payment state + audit + Payment outbox committed atomically
  -> PaymentObservedCustomerOutboxScheduler
  -> PaymentObservedCustomerOutboxDispatcher
  -> PaymentProjectionEventCommandMapper
  -> ObservedCustomerProjectionModuleAdapter
  -> ObserveCustomerUseCase
  -> Observed Customer persistence + processed-event deduplication
```

### Current mode

- transactional database outbox;
- scheduled in-process delivery;
- replay tests;
- metrics and health indicators;
- projection-side deduplication.

This is a valid modular-monolith integration and is not a mock.

### Contract

The current canonical mapping is code-level. Before Kafka or independent deployment, publish a versioned distributed event contract containing at least:

- event ID;
- event type and schema version;
- occurred-at timestamp;
- producer;
- aggregate/payment ID;
- correlation and causation IDs;
- partner/institution scope;
- public and external references;
- payment status and monetary facts;
- minimal customer/account references;
- payload classification.

### Error handling

- transient infrastructure/projection error: retry with backoff;
- duplicate: acknowledge as already processed;
- invalid permanent payload: quarantine and alert;
- retry exhaustion: operationally visible blocked state;
- replay: authorized, audited and idempotent.

## 3. Flow ASY-02 — Payment sends status callback to TresorPay

```text
Payment state transition
  -> callback outbox entry
  -> callback relay
  -> callback plan and payload
  -> detached JWS signer
  -> HTTPS callback adapter
  -> TresorPay callback endpoint
```

### Required contract fields

- callback event ID;
- payment/public/external reference;
- final or intermediate status;
- bank reference when disclosure is authorized;
- occurred-at timestamp;
- correlation ID;
- schema version;
- JWS key ID and signature headers.

### Retry policy

- network timeout, connection failure, 429 and eligible 5xx: bounded retry;
- 401/403: suspend and alert configuration/security owner;
- 404/410: permanent endpoint failure until partner remediation;
- invalid callback URL or signature configuration: quarantine;
- successful 2xx: mark delivered exactly once operationally;
- replay is allowed only with the same event ID.

## 4. Flow ASY-03 — Payment publishes distributed events

### Current state

Shared event envelope and transport abstractions exist. Production Kafka topology is not yet defined.

### Lot 5.5 decisions

- event catalogue;
- topic per domain or event family;
- partition key;
- schema format and registry;
- producer acknowledgements and idempotence;
- outbox-to-Kafka relay;
- consumer groups;
- retry topics versus local retry;
- DLQ;
- retention and replay;
- payload encryption/minimization;
- compatibility policy.

### Recommended event candidates

- `payment.received.v1`;
- `payment.verification-completed.v1`;
- `payment.funds-control-completed.v1`;
- `payment.posted.v1`;
- `payment.reversed.v1`;
- `payment.failed.v1`;
- `payment.reconciliation-required.v1`.

Names are provisional until contract approval.

## 5. Flow ASY-04 — Payment to Accounting

```text
Payment posted/finalized
  -> versioned accounting event or accounting command
  -> Accounting consumer
  -> accounting entry generation
  -> optional TFJ file generation
  -> optional SFTP/API transmission
  -> acknowledgement/rejection
  -> reconciliation status
```

### Required contract

- source event ID and payment ID;
- public/external/bank references;
- debit/credit accounts or accounting rules reference;
- amount, currency and value date;
- operation and transaction codes;
- institution/branch/partner;
- posting and reversal indicator;
- accounting batch/cut-off;
- reconciliation key.

### File/SFTP requirements if used

- exact file layout and encoding;
- naming and sequence;
- header/trailer/control totals;
- checksum and optional signature/encryption;
- temporary upload then atomic rename;
- host-key verification;
- acknowledgement/rejection file;
- archive and retention;
- duplicate file detection;
- restart and replay.

### Error rule

Accounting failure never silently changes a successful bank posting into an unposted payment. It creates a visible reconciliation/accounting-pending state.

## 6. Flow ASY-05 — Payment to Notification

```text
Payment business event
  -> Notification integration event
  -> Notification consumer
  -> recipient/consent resolution
  -> template rendering
  -> SMS/email/webhook provider
  -> delivery status
```

### Required contract

- event ID;
- notification reason;
- payment references safe for disclosure;
- recipient reference, not necessarily raw address;
- locale;
- template key/version;
- permitted variables;
- correlation ID;
- expiry time.

### Error rule

Notification failure does not roll back Payment. Retry transient provider failures, quarantine invalid recipient/template/configuration failures, and expose delivery status separately.

## 7. Delivery semantics

All asynchronous consumers SHALL:

1. use an immutable event ID;
2. persist a processed-event or equivalent deduplication record;
3. be safe under at-least-once delivery;
4. preserve ordering only where a documented partition key guarantees it;
5. expose lag/backlog, failure and oldest-message-age metrics;
6. support controlled replay;
7. avoid unbounded retry loops;
8. avoid placing secrets or unnecessary PII in event payloads.

## 8. Outbox lifecycle

Every production outbox requires:

- atomic write with business state;
- claim/lease semantics;
- bounded batch size;
- retry count and next-attempt time;
- terminal/quarantined status;
- delivered timestamp;
- cleanup and retention policy;
- replay operator identity and reason;
- health thresholds;
- runbook.
