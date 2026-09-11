# Regional Core Banking API

Autonomous Core Banking integration API of **La Régionale**, intended for SIXPAY CONNECT and other explicitly authorized Regional applications.

## Current lot

**R1 — Regional Contract Baseline**

R1 consolidates the Regional-owned OpenAPI V1 from approved Regional/bank evidence and SIXPAY compatibility evidence.

No Spring/OpenAPI generation is authorized in this lot.

## Contract governance

Canonical workspace:

`contracts/openapi/regional-core-banking-api-v1.yaml`

Current R1 status:

- `lifecycleStatus: DRAFT`
- `approvalStatus: PENDING_APPROVAL`
- `generationPolicy: REFERENCE_ONLY`
- `codeGenerationAllowed: false`

The contract must not be marked `APPROVED` until all blocking Regional banking evidence is available and explicit human approval is recorded.

SIXPAY contracts, clients, DTOs and tests are compatibility evidence only. They are not the canonical Regional server model.

## Blocking R1 evidence

The Payment Event provider payload still depends on the approved La Régionale mapping for the reduced `bkeve` / `bkmvti` fields and code tables. This evidence must be supplied/validated before the corresponding schema can be frozen and the complete Regional V1 can be approved.

See `documentation/contracts/R1_COMPATIBILITY_MATRIX.md`.

## Architecture

Target dependency direction:

`api -> application -> domain <- infrastructure`

Amplitude/Informix details belong only in `infrastructure/amplitude`.

## Source baselines

- Regional R1 starting revision: `main @ 857e440b415fc778cb4dddd5953e09e4958a6717`
- SIXPAY compatibility starter baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
