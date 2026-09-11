# REGIONAL CORE BANKING API — ChatGPT Project Instructions

## Mission
Develop `SIXGEN-Solutions/regional-core-banking-api` as an autonomous banking API of La Régionale, reusable by SIXPAY and other Regional applications.

## Source ownership
1. The canonical API contract is `contracts/openapi/regional-core-banking-api-v1.yaml` in the new repository.
2. SIXPAY contracts and code copied into `reference/sixpay/` are reference/compatibility evidence only.
3. Existing SIXPAY Java DTOs/models are NOT the source model of the Regional API.
4. The initial Regional V1 must preserve protocol compatibility with the requests/responses actually used by the current SIXPAY Core Banking clients where those operations are retained.
5. Compatibility means HTTP method/path, headers, JSON field semantics/types, response/status/error/recovery expectations — not Java class/package identity.

## Architecture
Use a single Spring Boot deployable application with capability-oriented packages and clean/hexagonal boundaries:
`api -> application -> domain <- infrastructure`.
Informix/Amplitude details stay in `infrastructure/amplitude`; they do not leak into the domain or API contract.

## Contract-first workflow
Requirements/evidence -> Regional OpenAPI -> review/approval -> generated HTTP boundary -> implementation -> contract/integration tests.

Generate only API boundary code (interfaces and transport DTOs) unless explicitly approved otherwise. Generated code must not contain business logic.

## Security baseline
Treat OAuth2 Client Credentials + mTLS as the current approved integration profile where supported by the source contracts. Environment endpoints, client credentials, certificates, trust stores and IP allowlists remain external configuration and must never be committed.

## Financial safety
Never blindly replay an operation with unknown financial outcome. Use authoritative lookup/recovery where the contract defines it. Preserve idempotency semantics.

## Documentation
Do not import SIXPAY governance wholesale. Copy only the provided starter pack and relevant reference evidence. Mark copied SIXPAY material as `REFERENCE_ONLY` and record its source SHA.

## Change control
No invention of endpoints, fields, statuses, procedures, Informix tables/columns, scopes or business rules. Contract/security/database/deployment decisions require human approval before implementation when not already supported by authoritative source material.
