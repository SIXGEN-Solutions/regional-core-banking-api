# R5.2 — Payment Confirmation / OTP — Bank integration decisions

Status: REGIONAL_DECISIONS_BASELINE — R5.2.0

This document records Regional implementation decisions validated for R5.2.
It does not approve a new endpoint, schema or OpenAPI wire change.

## Validated Regional decisions

### OTP authority
`regional-core-banking-api` is authoritative for OTP generation, challenge lifecycle,
secure verification, invalid-attempt counting/lockout, expiry, replacement,
revocation, idempotency/recovery and delivery orchestration.

Plaintext OTP is transient and must never be persisted, logged, audited, traced,
emitted in metrics/events or returned in an API response.

### Delivery-channel ownership
Delivery channels are selected exclusively by Regional Core Banking API deployment
configuration. Consumers such as SIXPAY do not select SMS or EMAIL and do not
supply a destination phone/email or SMTP configuration.

Regional configuration may enable SMS only, EMAIL only, or SMS and EMAIL
simultaneously. Destination contacts come from authoritative banking customer data.

### EMAIL
EMAIL delivery is owned by `regional-core-banking-api` through an outbound
infrastructure adapter using mail infrastructure authorized by La Régionale.
SMTP/relay endpoint, port, TLS parameters, service-account identity and credentials
are deployment/security configuration. Secrets remain external and must not be
committed or persisted in the technical database.

### SMS
The confirmed bank delivery chain is:
`Amplitude -> SMS database -> Kannel -> M-Target (SMPP) -> operators -> customer`.

Regional Core Banking API integrates with the bank-side SMS persistence mechanism;
it does not implement direct SMPP/M-Target transport. The physical SMS adapter is
blocked until La Régionale supplies authoritative table/schema, columns/types/
constraints, identifier generation, initial status, Kannel selection rules and
delivery/status update semantics. R5.2.0 invents no BKSMS SQL, column or procedure.

### Durable technical persistence
R5.2 uses a dedicated PostgreSQL technical persistence store owned by Regional Core
Banking API and separate from Amplitude banking data. It is intended for durable
challenge state, secure OTP verifier metadata, idempotency/recovery state and
delivery technical state.

Plaintext OTP, HMAC secret keys and SMTP credentials are forbidden from this store.
Physical PostgreSQL schema and migrations belong to R5.2.1 and are not frozen here.

### Multi-instance concurrency
Multi-instance correctness is based primarily on PostgreSQL transactions and
database uniqueness/concurrency guarantees, not JVM-local locks or an independently
introduced distributed lock as production source of truth. Exact locking/versioning
and uniqueness constraints are defined and tested in R5.2.1.

### Secrets
HMAC and SMTP secrets remain outside PostgreSQL and source control and are provided
by the environment secret-management mechanism. The R5.1 runtime-local random HMAC
key is not a production R5.2 solution.

## OpenAPI divergence requiring explicit contract evolution
The approved Regional V1 response currently contains singular
`PaymentConfirmationChallengeResult.deliveryChannel`.

The validated runtime policy permits SMS and EMAIL simultaneously. The singular
wire field therefore cannot faithfully represent every permitted delivery execution.

R5.2.0 records this as a known contract divergence. It does not choose a replacement
field/cardinality or compatibility policy and does not modify the OpenAPI.

Before a multi-channel representation is exposed on the wire:
1. the Regional representation must be explicitly designed;
2. consumer compatibility, including SIXPAY, must be checked;
3. the contract change must receive explicit human approval;
4. only then may the canonical OpenAPI and generated boundary be changed.

Until then, implementation must not invent a new response field or reinterpret the
singular field as representing multiple channels.

## Delivery timestamps
R5.1 evidence is insufficient to define one universal `sentAt` event for EMAIL and
SMS. R5.2 must derive `sentAt` from an explicitly defined delivery milestone rather
than challenge creation time. EMAIL semantics can be defined from the approved mail
adapter behavior. SMS semantics remain dependent on bank-provided SMS/Kannel evidence.

## Deferred bank/infrastructure evidence
Still external and not invented:
- physical SMS/BKSMS schema and write/status semantics;
- production SMTP/relay endpoint and security parameters;
- environment secret-management product/integration;
- exact SMS delivery acknowledgement milestone.

## R5.2 implementation sequence
1. R5.2.0 — decisions/documentation alignment.
2. R5.2.1 — durable PostgreSQL persistence and multi-instance concurrency.
3. R5.2.2 — externalized/versioned HMAC secret integration.
4. R5.2.3 — EMAIL delivery adapter using Regional infrastructure configuration.
5. R5.2.4 — delivery outcome and timestamp semantics.
6. R5.2.5 — SMS/BKSMS adapter after authoritative bank evidence.
7. R5.2.6 — robustness, recovery, security and integration closure tests.

## Boundaries
- Regional OpenAPI remains canonical in this repository.
- SIXPAY remains REFERENCE_ONLY compatibility evidence.
- SIXPAY Java models are not Regional source models.
- R5.2.0 changes documentation only.
- R5.2.0 performs no OpenAPI/Spring generation.
