# SIXPAY CONNECT Integration Landscape

## 1. Purpose

This document is the authoritative Phase 5.0 inventory of the integration surface implemented or planned in SIXPAY CONNECT.

It records the current state before any Phase 5 integration code is changed. It must be read together with:

- `integration-responsibility-matrix.md`;
- `synchronous-integration-flows.md`;
- `asynchronous-integration-flows.md`;
- `integration-error-taxonomy.md`.

The authoritative implementation revision is provided by the task invocation or selected execution environment.

## 2. Architectural rules

1. The `partner` module remains the golden implementation and package-structure reference.
2. Business ports remain owned by the business module that needs the capability.
3. Provider-specific adapters remain in the owning business module infrastructure unless a capability is truly cross-domain.
4. The transverse `backend/integration` module contains only reusable transport, event, serialization, correlation, retry, DLQ and test-support components.
5. Internal modular-monolith calls remain in-process Java calls.
6. Domain events and distributed integration events are separate contracts.
7. A contract marked `TO_DEFINE` is a blocking prerequisite for the relevant implementation lot.

## 3. Integration landscape

| ID | Flow | Direction | Mode | Current state | Primary implementation evidence | Contract status |
|---|---|---|---|---|---|---|
| INT-01 | TresorPay → SIXPAY Payment command | inbound | synchronous REST | implemented | `PaymentCommandController`, command DTOs, idempotency, security, audit and packaged OpenAPI | `documentation/contracts/external/payment-command-api-v1.yaml` |
| INT-02 | SIXPAY Payment → TresorPay status callback | outbound | asynchronous HTTP callback | implemented foundation | callback endpoint model, detached JWS signer, callback outbox relay and HTTP adapter | dedicated published callback contract must be confirmed |
| INT-03 | Payment → Customer Verification | internal | synchronous Java port | implemented | `CustomerVerificationModuleAdapter`, composition configuration and intermodule integration tests | Java application port; no external transport contract required while co-deployed |
| INT-04 | Customer Verification → Amplitude | outbound | synchronous REST | technically implemented | Amplitude mapper/client, OAuth2 token provider, mTLS SSL bundle, retry and observations | provider contract must be validated against authoritative Amplitude specification |
| INT-05 | Payment → Core Banking verification | outbound | synchronous provider call | adapter foundation only | `VerificationGateway`, `AmplitudeVerificationAdapter`, `AmplitudeBankingClient` | `TO_DEFINE` |
| INT-06 | Payment Funds Control | internal logical T0 gate | no standalone provider call in MVP | domain/application foundation exists | `FundsControlCheckType`, `FundsControlSnapshot`, Funds Control policy | fulfilled inside INT-07 T0 command |
| INT-07 | Payment → Core Banking T0 Payment event | outbound | one protected synchronous financial command | contract approved; implementation adaptation pending | Payment freezes reduced immutable event/entry snapshots then maps them to provider payload | `amplitude-payment-posting-api-v1.yaml`, `APPROVED`, `ACTIVE` |
| INT-08 | Payment → Core Banking T0 outcome lookup | outbound | synchronous provider query | contract approved; implementation adaptation pending | retain lookup by Payment reference and original Idempotency-Key | same approved T0 contract |
| INT-09 | Payment → Core Banking reversal | outbound | synchronous provider call | adapter foundation only | `ReversalGateway`, `AmplitudeReversalAdapter`, `AmplitudeBankingClient` | `TO_DEFINE` |
| INT-10 | Payment → Observed Customer | internal asynchronous projection | transactional outbox + scheduled in-process dispatch | implemented | Payment outbox, dispatcher, scheduler, mapper, replay tests and projection adapter | canonical mapping exists in code; distributed event contract not published |
| INT-11 | Payment → Accounting | internal Accounting-owned candidate source / scheduled T+1 constitution | planned but domain/application foundations exist | `PaymentAccountingCandidateSource`, `AccountingBatchConstitutionService`, `AccountingBatchGateway`; production candidate source not wired | internal boundary must expose immutable T0 financial-entry snapshot facts; Core Banking Accounting API contract remains `TO_DEFINE`; CSV deferred |
| INT-12 | Payment → Notification | internal/outbound asynchronous | planned | Notification module exists; Payment notification consumer/adapter not found | `TO_DEFINE` event and channel-provider contracts |
| INT-13 | Internal Payment query API | inbound | synchronous REST | implemented | query controller, security policy and projection adapters | `documentation/contracts/internal/payment-query-api-v1.yaml` |
| INT-14 | Internal Observed Customer query API | inbound | synchronous REST | implemented | query controller, masking, cursor, audit and health | `documentation/contracts/internal/observed-customer-query-api-v1.yaml` |
| INT-15 | Internal Payment audit query API | inbound | synchronous REST | contract present; implementation alignment to verify | Payment audit persistence and contract pack | `documentation/contracts/internal/payment-audit-query-api-v1.yaml` |

## 4. Existing ports, adapters, clients and events

### 4.1 Payment ports

Payment already contains focused boundaries for verification, funds control, posting, reversal and lookup. These boundaries must be retained. Phase 5 must not replace them with a generic `CoreBankingService`.

### 4.2 Payment provider adapters

Existing Amplitude adapters:

- `AmplitudeVerificationAdapter`;
- `AmplitudeFundsAdapter`;
- `AmplitudePostingAdapter`;
- `AmplitudeReversalAdapter`;
- `AmplitudeLookupAdapter`.

All depend on `AmplitudeBankingClient`. That interface explicitly states that no implementation is supplied in the foundation increment. The adapters are therefore conditional wrappers, not a production-ready external integration.

### 4.3 Customer Verification provider integration

Existing components include:

- `BankingCustomerVerificationPort`;
- `AmplitudeCustomerVerificationAdapter`;
- `AmplitudeCustomerVerificationClient`;
- `CoreBankingAccessTokenProvider`;
- `OAuth2CoreBankingAccessTokenProvider`;
- `AmplitudeCustomerVerificationMapper`;
- `BankingVerificationErrorClassifier`;
- `RetryingBankingCustomerVerificationAdapter`;
- `BankingVerificationObservation`;
- `AmplitudeCustomerVerificationConfiguration`;
- `BankingVerificationProperties`.

The integration already carries bearer authentication, correlation ID, request ID, configurable timeouts, retry and SSL-bundle based mTLS. Its remaining gap is authoritative provider-contract and sandbox validation.

### 4.4 Internal composition adapters

Existing bootstrap integration components include:

- `CustomerVerificationModuleAdapter`;
- `ObservedCustomerProjectionModuleAdapter`;
- `PaymentObservedCustomerOutboxConsumer`;
- `PaymentProjectionEventCommandMapper`;
- outbox dispatcher, scheduler, metrics, health and replay support.

These remain composition-root integrations while the connected modules are deployed together.

### 4.5 Events and outbox foundations

Shared primitives include:

- `IntegrationEventEnvelope`;
- `OutboxMessage`;
- `OutboxMessageSource`;
- `IntegrationEventTransport`.

Payment contains domain-event mapping, integration mapping, outbox persistence and callback outbox relay.

Kafka dependencies and abstractions are foundations only. They do not prove that production topics, publishers, consumers, retry topics or DLQs are complete.

## 5. Temporary, conditional and incomplete implementations

| Area | Classification | Required action |
|---|---|---|
| Payment `AmplitudeBankingClient` | explicit interface-only foundation | implement in Lot 5.4 after provider contracts are approved |
| Payment Amplitude adapters | conditional wrappers | retain and wire to the future client |
| standalone/development security | non-production profile support | exclude from secured-environment acceptance |
| in-memory/test doubles | test-only | keep isolated from production profiles |
| in-process Observed Customer dispatch | valid modular-monolith implementation | preserve; decide distributed transport in Lot 5.5 |
| Kafka support | partial foundation | define schemas, topics, guarantees, retry and DLQ |
| Accounting integration | missing | define and implement in Lot 5.6 |
| Notification integration | missing | define and implement in Lot 5.7 |
| Customer Verification Amplitude payload | externally unconfirmed | validate in Lot 5.3 |
| Payment callback publication | unclear | publish/confirm in Lot 5.2 |

## 6. Contract ownership

- TresorPay-facing Payment API: Payment team owns the SIXPAY provider contract.
- Payment internal APIs and ports: Payment team.
- Customer Verification internal port: Customer team.
- Amplitude external schema: Core Banking provider; SIXPAY teams own anti-corruption mappings.
- Observed Customer projection command: Customer team owns accepted semantics; Payment owns emitted facts.
- Distributed Payment events: Payment team owns schemas; consumers approve compatibility.
- Accounting contract: Accounting owns consumption/batching; Payment owns immutable T0 financial-event/entry source facts; Core Banking owns external API acceptance and effective accounting posting.
- Notification contract: Notification owns consumption and provider channels; Payment owns business facts.

## 7. Inputs required by future lots

### Lot 5.1

- approved correlation/request headers;
- canonical external-error model;
- timeout/retry defaults;
- event-envelope fields and versioning;
- Kafka naming/partition/retention conventions;
- observability tag rules;
- secrets/certificate ownership;
- integration test tooling matrix.

### Lot 5.2

- authoritative TresorPay examples;
- mTLS/JWT/API-key/signature decision;
- token claims and scopes;
- idempotency format and retention;
- callback registration and JWS rules;
- public error catalogue;
- sandbox URLs and certificates.

### Lot 5.3

- authoritative Amplitude endpoint/specification;
- OAuth2 endpoint, scopes and audience;
- certificate chain and rotation;
- field mappings and code tables;
- provider timeout/rate-limit expectations;
- idempotency/retry guarantees;
- sandbox fixtures.

### Lot 5.4

- one T0 financial execution operation covering mandatory Funds Control checks, protected Treasury resolution/use, debit and credit; plus authoritative outcome lookup and optional reversal;
- schemas and code mappings;
- amount, currency and value-date rules;
- bank references and reconciliation keys;
- posting/reversal idempotency;
- unknown-outcome resolution;
- compensation rules;
- operation SLAs and sandbox cases.

### Lot 5.5

- final distributed event catalogue;
- schema format/registry decision;
- topics, partitions, replication and retention;
- partition keys;
- consumer groups and owners;
- deduplication keys;
- retry/DLQ/replay policy;
- PII minimization.

### Lot 5.6

- Payment→Accounting candidate-source contract and eligibility semantics;
- authoritative TRESOR PAY status verification contract used for T+1 eligibility;
- Core Banking Accounting API batch submission/result contract for the MVP;
- cut-off calendar/timezone;
- acknowledgement/rejection/unknown-outcome formats;
- reconciliation and deduplication keys;
- CSV/file layout, encoding, naming, totals, checksum/signature and SFTP details only for the deferred file mode.

### Lot 5.7

- notification-trigger events;
- recipient and consent rules;
- providers and credentials;
- templates/languages/variables;
- webhook signature/retry;
- delivery callback contract;
- masking and retention.

### Lot 5.8

- environment topology;
- test identities/accounts/balances;
- test certificates/secrets;
- expected traces;
- failure injection;
- acceptance scenarios;
- dashboard, alert and runbook owners.

## 8. Exit assessment

Items marked `TO_DEFINE` are valid inventory findings but are not completed contracts. They are blocking inputs to the implementation lot named above.
