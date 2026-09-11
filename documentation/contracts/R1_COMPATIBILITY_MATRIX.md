# R1 — Regional V1 compatibility and approval matrix

## Status

- Repository: `SIXGEN-Solutions/regional-core-banking-api`
- R1 implementation baseline: `main @ 3ffa24a9dae5e03dc93dd2226455a8855c02ec2d`
- SIXPAY compatibility baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
- Execution mode: contract consolidation only / no code generation
- Overall contract approval: `APPROVED`
- Human approval: explicit Regional approval recorded in the R1 working session

## Governance

SIXPAY material remains `SIXPAY_REFERENCE / REFERENCE_ONLY`.
SIXPAY Java DTOs are consumer compatibility evidence only.
The Regional OpenAPI is the canonical V1 transport contract.

## Capability matrix

| Capability | Regional path(s) | Evidence / decision basis | R1 status |
|---|---|---|---|
| Customer / Account verification | `/api/v1/customers`, `/api/v1/customers/{customerReference}`, `/api/v1/customers/{customerReference}/accounts`, `/api/v1/customer-verifications` | SIXPAY compatibility contract + explicit Regional approval | APPROVED |
| Payment Confirmation / OTP | `/api/v1/payment-confirmation-challenges...` | SIXPAY compatibility contract + explicit Regional approval | APPROVED |
| Payment execution / recovery | `/api/v1/payment-events...` | SIXPAY wire DTO baseline + explicit Regional Payment Event decisions | APPROVED |
| Accounting T1 | `/api/v1/accounting-entries...` | SIXPAY compatibility contract + explicit Regional approval | APPROVED |
| End-of-day / TFJ lookup | `/api/v1/end-of-day-confirmations` | SIXPAY compatibility contract + explicit Regional approval | APPROVED |

## Approved Regional Payment Event decisions

- `providerEvent` and `providerEntry` use the existing SIXPAY wire DTO fields as the minimum required V1 field set.
- Future fields require an approved contract evolution.
- `operationCode` is supplied/configured by the calling application; Core Banking validates it against the Amplitude reference table.
- `nature` is supplied/configured by the calling application.
- `direction` is `D` for debit and `C` for credit.
- configurable business values remain caller-supplied rather than hard-coded by the Regional API.
- account reference format is `age-ncp-clc`.
- a Payment Event contains exactly two provider entries.
- receipt of a Payment Event directly triggers event execution.
- Core Banking controls execute before persistence.
- the event (`bkeve` equivalent) and the two entries (`bkmvti` equivalents) are written atomically.
- the bank reference is returned only after successful execution.
- an uncertain result is `UNKNOWN`; blind replay is forbidden.
- recovery uses the authoritative lookup by `paymentReference` or original `idempotencyKey` before any retry.

## Important exclusions

The SIXPAY inbound TFJ callback `/webhooks/v1/amplitude/end-of-day-confirmations`
is not part of the Regional Core Banking server surface.

No Spring/OpenAPI code generation is executed as part of R1.
