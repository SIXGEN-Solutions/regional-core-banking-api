# R2 — Spring Boot Bootstrap Baseline

## Revision

- Repository: `SIXGEN-Solutions/regional-core-banking-api`
- Starting revision: `main @ 92162217faae3d63ff51e9f40af8daf6a7f38986`
- Execution mode: local script applied by repository owner
- SIXPAY repository: reference-only; no modification

## Scope

R2 establishes only the executable application skeleton:

- Java 21;
- single Spring Boot deployable;
- Maven build;
- capability-oriented hexagonal packages;
- Actuator health/info;
- configuration profiles;
- foundational context/architecture tests;
- Dockerfile;
- Compose skeleton.

## Explicit exclusions

R2 does not:

- generate OpenAPI interfaces or DTOs;
- implement any approved banking endpoint;
- import SIXPAY Java models;
- create Informix/JDBC mappings;
- invent SQL, procedures, tables or columns;
- invent OAuth2 scopes, issuers or mTLS trust relationships;
- implement banking idempotency/recovery persistence.

Those belong to later approved lots.

## Package dependency direction

`api -> application -> domain <- infrastructure`

Domain packages must remain free of Spring, HTTP, JDBC, Amplitude and SIXPAY consumer dependencies.

## Validation gates

Targeted R2 gates:

```bash
mvn test
mvn verify
```

Container validation, when Docker is available:

```bash
mvn clean package
docker compose config
docker build .
```
