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

Customer closure decisions:

- the Regional canonical `CustomerVerificationRequest` signature remains unchanged;
- SIXPAY must align with the Regional request contract where the SIXPAY
  REFERENCE_ONLY contract differs;
- customers can be searched by NIU through `GET /api/v1/customers` and retrieved
  by canonical `customerReference` through
  `GET /api/v1/customers/{customerReference}`;
- the existing active-account filter is sufficient for this Customer increment;
  no additional blocked/opposed mapping is required for closure;
- `KycField.verified` and `verifiedAt` remain authoritative banking facts. Until
  La Régionale supplies their authoritative source/mapping, verification that
  requires those facts remains safely `INDETERMINATE`.

No Regional OpenAPI signature is changed by this closure increment.

R4 implementation revision audited before this patch:

`feat/customer-account-verification @ 7e4c99f0827f8eb4b0603d9e25ba44ddd0244f72`

## Customer Oracle integration mock

Start the database:

```bash
set ORACLE_APP_PASSWORD=local-test-password
docker compose -f compose.customer-oracle.yml up -d
```

Run the real JDBC/HTTP integration test:

```bash
set RUN_CUSTOMER_ORACLE_IT=true
set ORACLE_APP_PASSWORD=local-test-password
mvn -Pcustomer-oracle-mock -Dtest=CustomerOracleIT test
```

Run the application against the Oracle mock:

```bash
set ORACLE_APP_PASSWORD=local-test-password
mvn -Pcustomer-oracle-mock spring-boot:run -Dspring-boot.run.profiles=customer-oracle-mock
```

Customer OpenAPI group:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Customer OpenAPI JSON: `http://localhost:8080/v3/api-docs/customer`

The Oracle mock is test infrastructure only. It does not establish new production
Amplitude mappings or replace La Régionale banking evidence.



## R5.1 — Payment Confirmation / OTP

R5.1 introduces the provider-independent challenge/OTP domain, secure OTP verification,
idempotency/recovery ports and lifecycle tests behind the six approved Regional operations.
It does not implement BKSMS SQL or physical email transport; those require approved Regional
infrastructure evidence. No OpenAPI generation is performed by this patch.

See `documentation/architecture/R5_1_PAYMENT_CONFIRMATION_OTP_BASELINE.md`.


### R5.1 closure increment

R5.1 now wires the six approved Payment Confirmation operations through the generated
HTTP boundary and adds HTTP contract tests, Spring wiring, atomic in-process idempotency,
concurrency characterization, recovery tests and explicit delivery outcome semantics.

R5.2 has validated the target infrastructure direction:
- Regional Core Banking API is authoritative for OTP generation/lifecycle and delivery orchestration;
- delivery channels are Regional server configuration and may enable SMS, EMAIL or both;
- consumers do not select channels or destination contacts;
- durable challenge/idempotency/recovery state uses dedicated PostgreSQL, separate from Amplitude;
- multi-instance correctness relies primarily on PostgreSQL transactions and database uniqueness/concurrency guarantees;
- HMAC and SMTP secrets remain external to source control and PostgreSQL;
- EMAIL uses a Regional-owned outbound mail adapter with environment configuration;
- SMS follows the confirmed bank database -> Kannel -> M-Target path and awaits authoritative physical mapping.

The approved OpenAPI still exposes singular `deliveryChannel`, while Regional
configuration may enable SMS and EMAIL simultaneously. R5.2.0 records this known
contract divergence without modifying the canonical OpenAPI; a separately approved
contract evolution and compatibility review are required first.

See `documentation/architecture/R5_2_PAYMENT_CONFIRMATION_BANK_INTEGRATION_DECISIONS.md`.

### R5.2.1 — Durable PostgreSQL persistence

Payment Confirmation runtime persistence now targets the dedicated Regional PostgreSQL technical store.
Challenge and idempotency access is scoped by financial institution. Same-key concurrency is arbitrated by PostgreSQL transactions and the `(institution_code, idempotency_key)` primary key.

The schema artifact is `src/main/resources/db/r5_2_1/confirmation-postgresql.sql`. R5.2.1 does not introduce a migration product; applying that schema is an explicit deployment step until migration tooling is separately approved.

Runtime datasource values are external: `REGIONAL_TECHNICAL_DB_URL`, `REGIONAL_TECHNICAL_DB_USERNAME`, `REGIONAL_TECHNICAL_DB_PASSWORD`.

Optional PostgreSQL integration tests run when `REGIONAL_CONFIRMATION_IT_DB_URL`, `REGIONAL_CONFIRMATION_IT_DB_USERNAME` and `REGIONAL_CONFIRMATION_IT_DB_PASSWORD` are supplied.
