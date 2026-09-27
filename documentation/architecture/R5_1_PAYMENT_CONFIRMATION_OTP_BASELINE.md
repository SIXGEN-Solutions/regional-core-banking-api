# R5.1 — Payment Confirmation / OTP provider-independent baseline

Status: IMPLEMENTATION_BASELINE — this document does not approve a new contract.

R5.1 implements the provider-independent lifecycle behind the six already-approved Regional
Payment Confirmation operations. It changes no OpenAPI endpoint or schema.

Confirmed decisions:
- Regional Core Banking API generates and verifies OTP values.
- OTP plaintext is transient and must never be persisted or logged.
- OTP verification evidence uses HMAC-SHA-256 with an externally managed key.
- Three invalid attempts lock the challenge.
- Replacement creates a new challenge/OTP and invalidates the previous challenge.
- SMS and EMAIL are delivery capabilities behind an outbound port.
- No BKSMS SQL is implemented until La Regionale supplies authoritative physical mapping.
- No email transport is implemented until its Regional mechanism is approved.

The R5.1 in-memory repositories and no-op delivery adapter are test/reference adapters only.
They are not production persistence or delivery implementations. Durable persistence and physical
SMS/email integration belong to the next infrastructure increment.
