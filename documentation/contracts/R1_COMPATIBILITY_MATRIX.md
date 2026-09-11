# R1 — Regional V1 compatibility and evidence matrix

## Status

- Repository: `SIXGEN-Solutions/regional-core-banking-api`
- R1 baseline: `main @ 857e440b415fc778cb4dddd5953e09e4958a6717`
- SIXPAY reference baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
- Execution mode: contract analysis / no code generation
- Overall contract approval: `PENDING_APPROVAL`

## Rules

SIXPAY material is `SIXPAY_REFERENCE / REFERENCE_ONLY`.
SIXPAY Java DTOs are used only to verify actual consumer wire expectations.
They are not the Regional canonical model.

## Capability matrix

| Capability | Regional path(s) | Evidence available | R1 status | Blocking evidence |
|---|---|---|---|---|
| Customer / Account verification | `/api/v1/customers`, `/api/v1/customers/{customerReference}`, `/api/v1/customers/{customerReference}/accounts`, `/api/v1/customer-verifications` | SIXPAY approved Amplitude customer-verification contract and client evidence | READY_FOR_REGIONAL_REVIEW | Explicit Regional human approval |
| Payment Confirmation / OTP | `/api/v1/payment-confirmation-challenges...` | SIXPAY approved Amplitude payment-confirmation contract; no open SIXPAY contract decisions | READY_FOR_REGIONAL_REVIEW | Explicit Regional human approval |
| Payment execution / recovery | `/api/v1/payment-events...` | SIXPAY approved logical Payment Event contract and actual client DTO shape | BLOCKED_BANK_EVIDENCE | Approved La Régionale reduced `bkeve` / `bkmvti` field subset, code tables and mapping semantics |
| Accounting T1 | `/api/v1/accounting-entries...` | SIXPAY approved accounting-entry contract | READY_FOR_REGIONAL_REVIEW | Explicit Regional human approval; bank mapping evidence remains required for infrastructure implementation |
| End-of-day / TFJ lookup | `/api/v1/end-of-day-confirmations` | SIXPAY approved Amplitude TFJ lookup contract | READY_FOR_REGIONAL_REVIEW | Explicit Regional human approval |

## Important exclusions

The SIXPAY TFJ callback `/webhooks/v1/amplitude/end-of-day-confirmations` is a SIXPAY inbound endpoint and is not automatically part of the Regional Core Banking server surface.

Payment Event context lookups (`lastnumeroeveope`, `getdatecomptable`, `modenuit`) are not added to the Regional canonical surface by this R1 patch because the current Regional draft does not already contain them and R1 must not invent/add endpoints without explicit Regional approval.

## R1 blocking decision

The complete Regional V1 cannot be marked `APPROVED` while the Payment Event request contains an unresolved provider payload.

Required authority:
- `La Régionale banking evidence`

Required evidence:
- exact reduced provider event fields;
- exact reduced provider entry fields;
- code/value mappings;
- field mandatory/optional/nullability rules;
- any provider-side validation semantics needed at the HTTP boundary.

Until supplied and explicitly approved:
- keep `approvalStatus: PENDING_APPROVAL`;
- keep `generationPolicy: REFERENCE_ONLY`;
- keep `codeGenerationAllowed: false`;
- do not generate Spring/OpenAPI boundary code.
