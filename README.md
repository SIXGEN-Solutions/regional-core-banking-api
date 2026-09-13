# Regional Core Banking API

Autonomous Core Banking integration API of **La Régionale**, reusable by SIXPAY CONNECT and other explicitly authorized Regional applications.

## Previous foundation

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


## Known generator limitation

OpenAPI Generator `7.15.0` currently reports `Unknown type mutualTLS` while
processing the approved OpenAPI 3.1 mTLS security scheme.

This does **not** change the Regional contract. OAuth2 Client Credentials + mTLS
remain governed by the canonical OpenAPI contract and approved security decisions.

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

## Current lot — R4

**R4 — Customer / Account Verification**

R4 implements the Regional Customer capability behind the approved V1 contract:

- `GET /api/v1/customers`;
- `GET /api/v1/customers/{customerReference}`;
- `POST /api/v1/customer-verifications`.

`GET /api/v1/customers/{customerReference}/accounts` remains wired but is
explicitly reserved for the future Account coverage, especially RIB/IBAN behavior.

The HTTP boundary implements the R3-generated `CustomersApi`; generated transport
models remain separate from Regional domain/application models.

Bank access uses the approved legacy evidence through a JDBC anti-corruption
adapter with Informix and Oracle SQL dialect isolation. Customer lookup supports
`customerNumber`, `niu` (`bkcli.nid`) or both. The existing bank filter
`bkcom.ife='N' AND bkcom.cfe='N' AND bkcom.dev='001'` is the accepted active-account
filter for this Customer lot.

The R4 patch itself does not invoke OpenAPI generation. The
`r3-no-openapi-generation` profile now correctly wires the generator `skip`
parameter.

Known compatibility decision still open:

- the Regional canonical `CustomerVerificationRequest` is flattened
  (`accountReference`, `expectedNiu`, `expectedAccountHolder`, ...);
- the SIXPAY REFERENCE_ONLY contract currently describes nested
  `customer` / `account` subjects.

That conflict must not be silently reconciled. The Regional OpenAPI is not changed
by R4 without explicit human contract approval.

R4 implementation revision audited before this patch:

`feat/customer-account-verification @ 7e4c99f0827f8eb4b0603d9e25ba44ddd0244f72`

