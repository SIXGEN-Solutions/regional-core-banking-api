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

GitHub Actions runs the Maven verification/build and builds the Docker image.
For branch pushes, the image is published to Docker Hub under
`d22002/regional-core-banking-api`; `latest` is published only from `main`.

Local Docker Compose consumes the published image and does not build the API image:

```bash
docker compose pull regional-core-banking-api
docker compose up -d
docker compose ps
```

To run a specific immutable CI image, set `REGIONAL_API_IMAGE` in the local `.env`
to the required `d22002/regional-core-banking-api:<6-char-git-sha>` tag.

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

### R5.2.2 — Versioned external OTP HMAC keys

OTP HMAC material is no longer generated at startup. Runtime requires `REGIONAL_OTP_HMAC_ACTIVE_KEY_VERSION` and `REGIONAL_OTP_HMAC_KEYS`, supplied by the deployment secret-injection mechanism. The key ring uses comma-separated `<version>:<base64-secret>` entries. New challenges use the active version; existing challenges retain `otpKeyVersion` and remain verifiable while that previous key is retained. Secrets are never committed or stored in PostgreSQL. Old versions must not be retired while challenges or idempotent verification replays can still require them.


### R5.2.3 — EMAIL OTP adapter

R5.2.3 adds the SMTP/relay `EmailConfirmationDeliveryAdapter`, external EMAIL configuration,
and focused SMTP-client mock tests. The consumer never supplies the recipient address.

Production EMAIL recipient resolution is wired through the existing Customer banking
capability. `CustomerBankingPort.getCustomer(...)` exposes `CustomerIdentity.email()`, backed
by the first `bkemacli.email` row for `cli = customerReference`. Customer/KYC data quality is
owned by the Core Banking system; Payment Confirmation does not duplicate KYC validation.

No SMTP credential is committed and the adapter contains no OTP logging.


### R5.2.4 — Delivery semantics

Payment Confirmation keeps an internal delivery lifecycle distinct from the approved OpenAPI challenge status: CREATED, REQUESTED, ACCEPTED, FAILED and UNKNOWN. `sentAt` is populated only after adapter acceptance and is no longer derived from `createdAt`. A Noop adapter never reports successful delivery. No endpoint or OpenAPI schema/status is changed.


### R5.2.6 — Robustness and closure

R5.2.6 strengthens Payment Confirmation robustness tests for restart/recovery, concurrency, idempotency conflicts, OTP-attempt, replacement and expiration races, HMAC rotation, EMAIL outcomes, unknown delivery outcomes, OTP leakage and contract characterization. PostgreSQL integration tests remain environment-backed through `REGIONAL_CONFIRMATION_IT_DB_*`; skipped integration tests do not prove the multi-instance gate. SMS/BKSMS remains deferred pending authoritative La Régionale mapping evidence. No OpenAPI/Spring generation is executed in this lot.
\n\n### Technical PostgreSQL datasource\n\nThe Regional-owned technical store uses PostgreSQL database `regional_core_banking`, schema `core_banking`. Local Docker uses application user `regional_core_banking_app`; the local-only default password is `regional_core_banking_dev`. Runtime credentials remain external configuration. Flyway owns technical schema migrations. Customer/Amplitude banking access uses the separate `regional.banking.datasource` namespace and must never reuse the technical PostgreSQL datasource.\n\nFor R5.2.6 PostgreSQL integration tests:\n- `REGIONAL_CONFIRMATION_IT_DB_URL=jdbc:postgresql://localhost:5432/regional_core_banking?currentSchema=core_banking`\n- `REGIONAL_CONFIRMATION_IT_DB_USERNAME=regional_core_banking_app`\n- `REGIONAL_CONFIRMATION_IT_DB_PASSWORD=regional_core_banking_dev` (local only)\n

## Local Payment Confirmation / SMTP profile

R5.2.3b validated the EMAIL OTP path with a real SMTP connection against a Mailtrap Email Sandbox. The observed end-to-end result was: challenge creation -> EMAIL delivery accepted -> message captured by Mailtrap -> OTP submitted to the verification endpoint -> `VERIFIED` / `OTP_VERIFIED`. A separate test submitted after the five-minute TTL returned `EXPIRED` / `CHALLENGE_EXPIRED`, confirming expiry enforcement.

This validation proves the Regional SMTP adapter and OTP EMAIL flow against test SMTP infrastructure. Production recipient resolution is now wired through the existing Customer banking capability, which exposes the first `bkemacli.email` row for the customer reference. It does not establish La Régionale production SMTP/relay, network or security parameters. SMS/BKSMS remains deferred pending authoritative physical bank mapping evidence.

For local development, activate only the `local` profile. It contains the dedicated PostgreSQL technical datasource and Mailtrap Sandbox connection and also selects the real EMAIL delivery adapter used for local SMTP testing.

Secrets are intentionally excluded from committed profiles. Copy `config/application-local-secrets.example.yml` to `config/application-local-secrets.yml`, then fill the Mailtrap username/password and a local 32-byte Base64 HMAC key once. The real local secrets file is gitignored and imported automatically by `application-local.yml`.

Start without re-entering environment variables:

```bash
mvn -Pr3-no-openapi-generation spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local secrets file is developer-machine configuration only. HMAC and SMTP production secrets remain external deployment secrets and must never be committed or stored in PostgreSQL.


### Production EMAIL OTP wiring

Payment Confirmation resolves the production EMAIL recipient through the existing Customer
banking capability: `ConfirmationRecipientPort` delegates to
`CustomerBankingPort.getCustomer(...)` and uses `CustomerIdentity.email()`. The Customer JDBC
adapter already returns the first `bkemacli.email` row for the customer reference.

The Noop delivery adapter is removed. Outside `local`, the real
`EmailConfirmationDeliveryAdapter` is used with externally supplied SMTP configuration.
The `local` profile keeps its fixed test recipient only for Mailtrap developer testing.

Production activation therefore remains conditional on La Régionale supplying the real
SMTP/relay/security/network parameters and secrets. No production SMTP credentials are committed.

Operational activation steps and the exact production configuration inventory are documented in `documentation/runbooks/REGIONAL_PRODUCTION_EMAIL_OTP_ACTIVATION.md`.

## Local Docker stack — R5

The local Docker stack runs the Regional Core Banking API, its Regional-owned
PostgreSQL technical persistence, and the existing Oracle Customer/Account mock.

The Oracle service remains test infrastructure only. It does not establish or
replace production Amplitude/Informix mappings.

### Image prerequisite

The API image is built by GitHub Actions after Maven verification and published to
Docker Hub as `d22002/regional-core-banking-api`. Local Compose pulls that image;
it does not build the API container from the local workspace.

### Start

Optionally copy `.env.example` to `.env`, then pull and start:

    docker compose pull regional-core-banking-api
    docker compose up -d

Application: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

Actuator health: `http://localhost:8080/actuator/health`

PostgreSQL is exposed on host port `15432` by default and Oracle mock on `1521`.

### Stop

    docker compose down

To also remove local database volumes:

    docker compose down -v

The example passwords and HMAC material are local-development values only.
Production database, HMAC, SMTP, OAuth2, mTLS and banking connectivity parameters
remain external deployment configuration.

## R6 — Payment Execution / Recovery

R6 wires the three approved Regional Payment Execution operations behind the generated
HTTP boundary and adds a Regional domain/application model for execution and authoritative
recovery. Financial execution is never blindly replayed: an `UNKNOWN` result is returned
as HTTP 202 and must be resolved through the payment-reference or original-idempotency-key
lookup before any retry.

The approved Regional V1 does not expose the historical SIXPAY context endpoints
(event-number allocation, accounting date, night mode), so R6 does not invent them.

Physical Amplitude/Informix posting remains fail-closed until La Régionale supplies and
approves the exact execution/recovery mapping, transaction boundary, execution checks,
bank-reference source and authoritative idempotency lookup. The current adapter therefore
returns service unavailable rather than simulating a financial result.

See `documentation/architecture/R6_PAYMENT_EXECUTION_RECOVERY_BASELINE.md`.

## Dedicated PostgreSQL integration-test datasource

Destructive JDBC confirmation persistence tests use a database that is
physically separate from the local/runtime technical datasource.

See
`documentation/testing/POSTGRESQL_INTEGRATION_TEST_DATASOURCE.md` and
`.env.test.example`.

The dedicated test container is started with:

```bash
docker compose -f compose.test-postgres.yml up -d
```

The runtime `regional_core_banking` database must not be used by
`JdbcConfirmationPersistenceTest`.

