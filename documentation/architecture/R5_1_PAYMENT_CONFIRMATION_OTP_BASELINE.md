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

Durable persistence, production secret management, BKSMS mapping and email transport belong to R5.2.
