# R3 — Contract Generation & CI Baseline

## Revision

- Repository: `SIXGEN-Solutions/regional-core-banking-api`
- Branch: `feat/contract-generation-ci`
- Starting SHA: `bf886255254724dd857301274cb2942b98e7044a`
- Execution mode: local Python patch applied by repository owner
- SIXPAY: reference-only / no modification

## Approved contract

Canonical contract:

`contracts/openapi/regional-core-banking-api-v1.yaml`

Observed before R3:

- OpenAPI `3.1.0`
- contract version `1.0.0`
- `approvalStatus: APPROVED`
- `codeGenerationAllowed: true`

## Generation decision

R3 pins OpenAPI Generator Maven Plugin `7.15.0`.

Approved generated scope:

1. HTTP API interfaces;
2. transport DTOs/models.

Explicitly excluded from generation:

- business/application services;
- domain models;
- persistence/adapters;
- Amplitude/Informix models;
- security/business policies;
- SIXPAY Java representations.

Generated packages:

- `com.regional.corebanking.generated.api`
- `com.regional.corebanking.generated.model`

Output directory:

`target/generated-sources/openapi`

Generated output is build-local and is not committed.

## CI gates

GitHub Actions is configured for:

- contract approval metadata guard;
- Java 21 setup;
- Maven `verify`;
- architecture tests;
- OpenAPI boundary generation during Maven lifecycle;
- Docker build;
- Docker Compose config;
- pull-request dependency review.


## Known toolchain limitation — OpenAPI 3.1 mutualTLS

OpenAPI Generator `7.15.0` emits messages such as:

`Unknown type mutualTLS found in the security definition`

when processing the approved OpenAPI 3.1 `mutualTLS` security scheme.

This is treated as a generator/toolchain limitation, not as authorization to
change the Regional security contract.

R3 therefore preserves the approved contract semantics:

- OAuth2 Client Credentials;
- mTLS where defined by the approved Regional contract;
- no invented scopes or trust relationships.

The generated Java HTTP boundary must not be treated as the source of truth for
transport security semantics. The canonical OpenAPI contract remains authoritative.

## Safety

No endpoint, schema, banking mapping, Informix structure, OAuth2 scope or trust relationship is introduced by R3.

The patch script itself intentionally does not invoke Maven/OpenAPI generation.
