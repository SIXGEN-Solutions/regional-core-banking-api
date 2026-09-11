# Regional Core Banking API

Autonomous Core Banking integration API of **La Régionale**, reusable by SIXPAY CONNECT and other explicitly authorized Regional applications.

## Current lot

**R2 — Spring Boot Bootstrap**

R2 initializes the deployable Java 21 / Spring Boot application shell, Maven build, hexagonal package boundaries, runtime configuration skeleton, foundational tests and containerization.

R2 introduces **no banking endpoint implementation** and performs **no OpenAPI generation**.

## Contract governance

Canonical approved contract:

`contracts/openapi/regional-core-banking-api-v1.yaml`

Current contract status:

- `lifecycleStatus: APPROVED`
- `approvalStatus: APPROVED`
- `generationPolicy: ACTIVE`
- `codeGenerationAllowed: true`

OpenAPI generation is intentionally deferred to **R3 — Contract generation and CI**.

## Runtime baseline

- Java 21
- Spring Boot
- Maven
- single deployable application
- Actuator health/info
- graceful shutdown
- Docker image
- Docker Compose local skeleton

## Architecture

Dependency direction:

`api -> application -> domain <- infrastructure`

Each banking capability owns:

```text
<capability>/
├─ api/
├─ application/
│  ├─ port/in/
│  ├─ port/out/
│  └─ service/
├─ domain/
└─ infrastructure/
   └─ amplitude/
```

Capabilities currently scaffolded:

- customer
- confirmation
- payment
- accounting
- tfj

Supporting packages:

- security
- audit
- common
- configuration

No Informix schema, SQL, stored procedure, banking mapping or SIXPAY Java model is introduced by R2.

## Build

```bash
./mvnw verify
```

or, if the Maven Wrapper has not yet been generated locally:

```bash
mvn verify
```

## Run

```bash
mvn spring-boot:run
```

Health endpoint:

```text
GET /actuator/health
```

## Container

Build the application first:

```bash
mvn clean package
docker compose up --build
```

Environment-specific credentials, URLs, OAuth2 parameters, certificates and trust material must stay outside the repository.

## Source baselines

- Regional R2 starting revision: `main @ 92162217faae3d63ff51e9f40af8daf6a7f38986`
- SIXPAY compatibility starter baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
