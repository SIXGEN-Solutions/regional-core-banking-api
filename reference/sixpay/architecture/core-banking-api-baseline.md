# CB-1 — Core Banking API Baseline

## Purpose

This document is the authoritative architecture baseline for Core Banking APIs
consumed by SIXPAY CONNECT under the task-selected authoritative revision.

## Governance decision D00-CB-OWNERSHIP

The Core Banking APIs required by SIXPAY are target integration contracts
designed within the SIXPAY/Core Banking programme and implemented by the
Core Banking delivery team.

They are not treated as pre-existing provider APIs whose wire contracts must
first be discovered from La Regionale.

Consequences:

- SIXPAY may define the target endpoint, method, schemas, status/error model,
  idempotency, recovery and security contract required by its use cases;
- SIXPAY may implement the corresponding outbound clients once a target
  contract is approved and code generation is explicitly allowed;
- the Core Banking team implements the server side against the approved target
  contract;
- only decisions that genuinely belong to bank business policy or runtime
  environment remain external, for example resend/cooldown policy,
  environment URLs, certificates, trust chains, SLA values and
  environment-specific operational limits.

D00-CB-OWNERSHIP does not implicitly approve unresolved business-policy or
runtime parameters.

## Status model

### Contract approval

- `APPROVED`: target contract approved by the required SIXPAY/Core Banking
  governance.
- `PENDING_APPROVAL`: target contract exists but one or more programme,
  business or Security decisions remain unresolved.
- `PENDING_BANK_APPROVAL`: remains valid for contracts that genuinely depend
  on approval of a pre-existing external bank/provider API. It is not used for
  Core Banking target-contract design decisions governed by D00-CB-OWNERSHIP.
- `TO_DEFINE`: no stable target contract exists.

### MVP requirement

- `REQUIRED`: required by the target MVP.
- `OPTIONAL`: used only if the bank confirms support and the project enables it.

`OPTIONAL` is not an approval status.

## Inventory

| Capability | Contract | Owner | Contract status | MVP |
|---|---|---|---|---|
| Customer discovery | `amplitude-customer-verification-api-v1.yaml` | Customer | `APPROVED` | `REQUIRED` |
| Customer/KYC/account verification | `amplitude-customer-verification-api-v1.yaml` | Customer | `APPROVED` | `REQUIRED` |
| Payment confirmation / OTP challenge | `amplitude-payment-confirmation-api-v1.yaml` | Payment | `APPROVED` | `REQUIRED` |
| T0 Payment event execution: SIXPAY-built immutable event/entry snapshot + mandatory Core Banking controls + atomic debit/credit | `amplitude-payment-posting-api-v1.yaml` | Payment | `APPROVED` | `REQUIRED` |
| T0 financial outcome lookup by Payment reference and Idempotency-Key | `amplitude-payment-posting-api-v1.yaml` | Payment | `APPROVED` | `REQUIRED` |
| Reversal + reversal lookup | `amplitude-payment-posting-api-v1.yaml` | Payment | `PENDING_APPROVAL` | `OPTIONAL — PENDING_PROGRAMME_ENABLEMENT` |
| T+1 accounting batch submission | physical provider contract `TO_DEFINE`; implementation shape already exists behind `AccountingBatchGateway` | Accounting | `TO_DEFINE` | `REQUIRED — API MVP` |
| T+1 accounting/TFJ result confirmation + fallback lookup | `amplitude-end-of-day-confirmation-api-v1.yaml` | Accounting / Payment lifecycle | `PENDING_APPROVAL` | `REQUIRED` |
| T+1 CSV/file accounting submission | contract `TO_DEFINE` | Accounting | `TO_DEFINE` | `DEFERRED_FUTURE` |

## Payment execution ordering

For the MVP Payment flow, Core Banking capabilities are ordered as follows:

```text
TRESOR PAY payment request
        ↓
SIXPAY durable Payment persistence
        ↓
RECEIVED
        ↓
Amplitude customer/account/KYC verification
        ↓
VERIFIED only
        ↓
Payment confirmation challenge / OTP
        ↓
successful customer confirmation
        ↓
later execution-time funds control and posting
```

The Customer Verification capability is a mandatory prerequisite of Payment
Confirmation. A `REJECTED` or `INDETERMINATE` customer/account/KYC verification
must not create or send an OTP challenge.

The canonical `customerReference` and debtor `accountReference` used by Payment
Confirmation come from the completed VERIFIED Amplitude verification result.
They are not inferred from NIU, TRESOR PAY subscription references or other
non-authoritative identifiers.

Customer Verification remains distinct from execution-time Funds Control.

After successful OTP/customer confirmation, the eight mandatory Funds Control
checks are evaluated inside the same protected synchronous T0 Core Banking
financial command that resolves/uses the configured Treasury destination,
debits the customer account and credits the Treasury account.

The MVP does not perform a standalone read-only funds check followed by a later
debit. Other banking channels may change the same account concurrently, so
financial availability must be decided by Core Banking at execution time.

The canonical detailed baseline is:
`documentation/architecture/integration/payment-financial-execution-and-accounting-eod-baseline.md`.

## Target Core Banking signatures

### Customer verification

`POST /api/v1/customer-verifications`

Customer discovery:
- `GET /api/v1/customers`
- `GET /api/v1/customers/{customerReference}`
- `GET /api/v1/customers/{customerReference}/accounts`

### T0 financial execution

Approved physical contract:

`POST /api/v1/payment-events`

Payment freezes a reduced immutable financial-event snapshot and immutable
financial-entry snapshots before submission. The Payment-owned Amplitude mapper
converts those snapshots into the provider payload corresponding to `bkeve` plus
`bkmvti[]`.

The full historical Amplitude entity/table model is not imported into the
SIXPAY domain or persisted as-is.

Core Banking evaluates all eight mandatory execution-time banking controls and
owns atomic execution of the submitted debit/credit event.

Application integration security is OAuth2 Client Credentials plus mTLS.
Network controls inside the bank SI remain defense in depth.

A financial command with an uncertain transport outcome is never blindly retried.

### T0 authoritative lookup

Both approved recovery mechanisms are retained:

- `GET /api/v1/payment-events/{paymentReference}`
- `GET /api/v1/payment-events/idempotency/{idempotencyKey}`

### Reversal

The physical reversal contract is `TO_DEFINE`.

Reversal is `OPTIONAL` until the programme explicitly enables it and approves
its endpoint, payload, authorization, idempotency, recovery and operational
semantics.

No legacy `/api/v1/payment-postings/...` reversal URL is part of the approved
T0 Payment Event surface.

### Accounting T+1 / TFJ / EOD

Accounting first constitutes a batch from financially successful Payments whose
TRESOR PAY status evidence satisfies the accounting-eligibility rule.

Payment exposes immutable financial-entry snapshot facts through an approved
internal boundary. Accounting never reads Payment JPA repositories or provider
entities directly.

For the MVP, Accounting submits a batch of frozen SIXPAY-generated accounting
lines through `AccountingBatchGateway` to the Core Banking Accounting API.
Core Banking validates and effectively posts/accounts those submitted lines and
returns authoritative results.

The final physical Accounting API endpoint/path and complete wire schema remain
`TO_DEFINE` until the dedicated T1 contract is formalized.

`amplitude-end-of-day-confirmation-api-v1.yaml` remains the result-confirmation /
reconciliation contract, not the batch-submission contract.

CSV/file submission is explicitly deferred until a separate approved file
contract exists.

## Resolved documentary drift

1. D00-CB-OWNERSHIP replaces the former provider-discovery approval model for
   Core Banking target contracts.
2. `OPTIONAL` describes MVP requirement, not contract approval.
3. `amplitude-payment-verification-api-v1` is obsolete/nonexistent;
   execution-time funds checking belongs to
   `amplitude-payment-posting-api-v1.yaml`.
4. `AmplitudeBankingClient` is legacy foundation debt; narrow
   capability-specific clients are the target.
5. Amplitude owns banking facts; SIXPAY owns SIXPAY Customer enrollment and
   partner-subscription lifecycle.
6. Environment URLs, certificates, trust material, SLA values and
   environment-specific limits remain external configuration and do not block
   approval of a stable logical target contract.

## Promotion to APPROVED

Promotion requires traceable programme approval of the target endpoint/method,
request/response schema, security profile, error/status model and
idempotency/recovery semantics where applicable.

Environment-specific URLs, certificates, trust chains, SLA values and
operational limits are validated during environment/sandbox certification and
are not prerequisites for approving the logical target contract unless they
change its semantics.
