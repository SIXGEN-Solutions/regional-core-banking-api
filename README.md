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

- `lifecycleStatus: APPROVED`
- `approvalStatus: APPROVED`
- `generationPolicy: ACTIVE`
- `codeGenerationAllowed: true`

The Regional V1 transport contract has received explicit human approval.
R1 itself performs no Spring/OpenAPI generation; generation belongs to the later contract-generation lot.

SIXPAY contracts, clients, DTOs and tests are compatibility evidence only. They are not the canonical Regional server model.

## R1 approval

The minimum Payment Event provider field set, account-reference format,
direction mapping, atomic execution semantics and UNKNOWN/recovery semantics
have been explicitly approved for Regional V1.

See `documentation/contracts/R1_COMPATIBILITY_MATRIX.md`.

## Architecture

Target dependency direction:

`api -> application -> domain <- infrastructure`

Amplitude/Informix details belong only in `infrastructure/amplitude`.

## Source baselines

- Regional R1 starting revision: `main @ 857e440b415fc778cb4dddd5953e09e4958a6717`
- SIXPAY compatibility starter baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
