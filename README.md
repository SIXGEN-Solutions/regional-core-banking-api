# Regional Core Banking API

Autonomous Core Banking integration API of **La Régionale**, intended for SIXPAY CONNECT and other explicitly authorized Regional applications.

## Current lot

**R0 — Repository Bootstrap / Starter Pack Import**

R0 establishes repository governance, architecture documentation, the Regional OpenAPI draft workspace, and classified SIXPAY compatibility references. It deliberately contains **no Spring Boot implementation** and performs **no OpenAPI/code generation**.

## Contract governance

The Regional contract workspace is `contracts/openapi/regional-core-banking-api-v1.yaml`. During R0 it remains:

- `lifecycleStatus: DRAFT`
- `approvalStatus: PENDING_APPROVAL`
- `generationPolicy: REFERENCE_ONLY`
- `codeGenerationAllowed: false`

No API boundary code may be generated until explicit human approval.

SIXPAY contracts, documentation and implementation evidence are compatibility references only. SIXPAY Java DTOs/models are not the canonical Regional model.

## Architecture

Target dependency direction:

`api -> application -> domain <- infrastructure`

Amplitude/Informix details belong only in `infrastructure/amplitude`. Regional transport models, Regional application/domain models, Amplitude anti-corruption models, Informix/JDBC representations, and SIXPAY consumer-side models remain separate.

## Source baselines

- Regional bootstrap starting revision: `main @ 0187d957fe3ed885146ced3e670f540a89c26891`
- SIXPAY compatibility starter baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`

See `documentation/SOURCE_MANIFEST.md` and `documentation/SOURCE_CLASSIFICATION.md`.
