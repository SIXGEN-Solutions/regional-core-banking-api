# Regional Core Banking API

Autonomous Core Banking integration API of **La Régionale**, reusable by SIXPAY CONNECT and other explicitly authorized Regional applications.

## Current lot

**R3 — Contract Generation & CI**

R3 freezes the OpenAPI generation toolchain and CI/gate configuration for the approved Regional V1 contract.

The canonical contract remains:

`contracts/openapi/regional-core-banking-api-v1.yaml`

R3 does not change banking endpoints, schemas or business semantics.

## Contract governance

Current Regional V1 contract status:

- `lifecycleStatus: APPROVED`
- `approvalStatus: APPROVED`
- `generationPolicy: ACTIVE`
- `codeGenerationAllowed: true`

SIXPAY contracts, DTOs and clients remain compatibility/reference evidence only.

## OpenAPI generation boundary

Pinned toolchain:

- OpenAPI Generator Maven Plugin: `7.15.0`
- generator: `spring`
- Java baseline: `21`
- Spring Boot baseline: `3.5.6`

Generation scope is intentionally limited to:

- HTTP API interfaces;
- OpenAPI transport DTOs/models.

Generated package roots:

```text
com.regional.corebanking.generated.api
com.regional.corebanking.generated.model
```

Generated code must contain no business logic and must not become the Regional domain model.

Generated output is build-local:

```text
target/generated-sources/openapi/
```

It is not committed to the repository.

## Architecture

Target dependency direction:

`api -> application -> domain <- infrastructure`

Amplitude/Informix implementation stays under capability `infrastructure/amplitude`.

SIXPAY Java models must never be imported into Regional production code.

## CI gates

The R3 CI pipeline is configured to cover:

- approved-contract metadata guard;
- Maven verify;
- architecture tests;
- generated-boundary architecture guard;
- dependency/security scanning;
- Docker build validation.

## Local commands

Normal verification after R3 configuration is applied:

```bash
./mvnw verify
```

To inspect only the non-generation bootstrap/tests while preparing the R3 patch:

```bash
./mvnw -Pr3-no-openapi-generation test
```

## Container

```bash
./mvnw package
docker build .
docker compose config
```

Environment-specific endpoints, credentials, certificates, private keys and trust material remain external configuration.

## Source baselines

- Regional R3 starting revision: `feat/contract-generation-ci @ bf886255254724dd857301274cb2942b98e7044a`
- SIXPAY compatibility starter baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
