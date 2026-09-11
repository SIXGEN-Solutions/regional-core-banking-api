# CB-2 — Core Banking Client Consolidation

## Decision

SIXPAY no longer exposes or wires a generic `AmplitudeBankingClient`.

Provider-specific clients remain inside the owning business domain and are
split by capability.

## Ownership and client matrix

| Capability | Owning module | SIXPAY port | Provider client |
|---|---|---|---|
| Customer verification | Customer | `BankingCustomerVerificationPort` | `AmplitudeCustomerVerificationClient` |
| Payment account/funds checks | Payment | `VerificationGateway`, `FundsGateway` | `AmplitudeAccountFundsClient` |
| Payment event execution | Payment | existing posting/execution boundary to align | `AmplitudePaymentEventClient` |
| Payment event lookup | Payment | existing lookup boundary to align | `AmplitudePaymentEventStatusClient` |
| Reversal | Payment | `ReversalGateway` | `AmplitudeReversalClient` |
| Fund reservation | Payment | reservation port | `AmplitudeFundsReservationClient` |
| Fund release | Payment | release port | `AmplitudeFundsReleaseClient` |
| TFJ/EOD | Accounting / Payment lifecycle | dedicated EOD boundary | dedicated EOD integration |

The shared `backend/integration` module remains provider-neutral and must not
contain Amplitude payloads, mappings or banking business semantics.

## Bank-approved endpoints

Approved T0 operations:

- `POST /api/v1/payment-events`
- `GET /api/v1/payment-events/{paymentReference}`
- `GET /api/v1/payment-events/idempotency/{idempotencyKey}`

Payment owns the reduced immutable financial snapshot and the Amplitude-specific
mapping used to construct the provider event. `backend/integration` remains
provider-neutral.

The T1 Accounting submission endpoint remains `TO_DEFINE`.

## Safety

Read-only operations may use bounded retry according to the integration policy.

Financial commands do not use blind retry after an uncertain transport outcome.
Payment Event execution uncertainty is resolved through the approved lookup
operations before any replay decision.

## Legacy removed

The following Payment infrastructure types are removed:

- `AmplitudeBankingClient`
- legacy `AmplitudePostingAdapter`
- legacy `AmplitudeLookupAdapter`
- legacy `AmplitudeReversalAdapter`

`AmplitudeVerificationAdapter` and `AmplitudeFundsAdapter` remain in Payment,
but now depend only on `AmplitudeAccountFundsClient`.

## Exit criteria

- no production source references `AmplitudeBankingClient`;
- Payment ports are wired only through capability-specific clients;
- Customer verification remains owned by Customer;
- provider-neutral integration code remains provider-neutral;
- approved endpoint metadata is synchronized with OpenAPI;
- Payment tests and architecture tests pass.
