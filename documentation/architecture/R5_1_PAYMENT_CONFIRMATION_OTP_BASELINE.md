# R5.1 — Payment Confirmation / OTP provider-independent baseline

Status: IMPLEMENTATION_BASELINE — this document does not approve a new contract.

R5.1 implements the provider-independent lifecycle behind the six already-approved Regional
Payment Confirmation operations. It changes no OpenAPI endpoint or schema.

## Closed R5.1 concerns

- Atomic in-process idempotency execution for the R5.1 reference repository.
- Recovery by original Idempotency-Key.
- Concurrency characterization for same-key create.
- Delivery outcomes: DELIVERED, CONFIRMED_FAILURE, UNKNOWN.
- CONFIRMED_FAILURE maps to DELIVERY_FAILED.
- UNKNOWN maps to DEPENDENCY_RESULT_UNKNOWN.
- Six existing Regional HTTP operations wired through the generated boundary.
- HTTP contract tests for the six routes.
- Spring wiring for the R5.1 reference implementation.
- SIXPAY remains REFERENCE_ONLY compatibility evidence.

## Infrastructure boundary

The in-memory repositories and no-op delivery adapter are R5.1 reference/test infrastructure only.
They are not production persistence or physical delivery mechanisms.

R5.2 supersedes the previously open infrastructure choices:
- Regional Core Banking API is authoritative for OTP generation/lifecycle, verification and delivery orchestration.
- Delivery channels are Regional server configuration, never consumer-selected; SMS and EMAIL may be simultaneous.
- EMAIL uses a Regional-owned outbound adapter with externalized mail infrastructure/secrets.
- SMS follows the bank-confirmed database -> Kannel -> M-Target chain; physical mapping awaits bank evidence.
- Durable challenge/idempotency/recovery state uses dedicated PostgreSQL, separate from Amplitude.
- Production multi-instance correctness relies primarily on PostgreSQL transactions and database uniqueness/concurrency guarantees.
- HMAC and SMTP secrets remain outside database/source control.

The approved OpenAPI still exposes singular `deliveryChannel`; this is a known
divergence requiring explicit contract evolution/approval before modification or generation.

See `R5_2_PAYMENT_CONFIRMATION_BANK_INTEGRATION_DECISIONS.md`.
