# PostgreSQL integration-test datasource

`JdbcConfirmationPersistenceTest` is a destructive integration test: its setup
creates the confirmation persistence objects when necessary and clears
`regional_confirmation_idempotency` and `regional_confirmation_challenge`.

It must never run against the application's runtime technical database.

## Isolation

Runtime/local PostgreSQL remains unchanged:

- database: `regional_core_banking`
- default host port: `15432`

Confirmation integration tests use a dedicated ephemeral PostgreSQL instance:

- database: `regional_core_banking_test`
- default host port: `15433`
- user: `regional_core_banking_test`
- storage: Docker `tmpfs` (not persisted after container removal)

The Java test contains a fail-closed guard and refuses any
`REGIONAL_CONFIRMATION_IT_DB_URL` whose database name is not exactly
`regional_core_banking_test`.

## Start the integration-test database

PowerShell:

```powershell
docker compose -f compose.test-postgres.yml up -d
docker compose -f compose.test-postgres.yml ps
```

## Configure the current PowerShell session

```powershell
$env:REGIONAL_CONFIRMATION_IT_DB_URL = "jdbc:postgresql://localhost:15433/regional_core_banking_test?currentSchema=core_banking"
$env:REGIONAL_CONFIRMATION_IT_DB_USERNAME = "regional_core_banking_test"
$env:REGIONAL_CONFIRMATION_IT_DB_PASSWORD = "regional_core_banking_test"
```

These values are development/test defaults only. CI may provide different
credentials, but the database name must remain `regional_core_banking_test`
unless the guard is deliberately reviewed and changed.

## Run only the JDBC integration test

```powershell
./mvnw -Pr3-no-openapi-generation -Dtest=JdbcConfirmationPersistenceTest test
```

## Run the complete test suite

```powershell
./mvnw -Pr3-no-openapi-generation test
```

If `REGIONAL_CONFIRMATION_IT_DB_URL` is absent, the JDBC integration test is
skipped by its existing JUnit assumption. Other tests can therefore run
without PostgreSQL.

## Stop the test database

```powershell
docker compose -f compose.test-postgres.yml down
```

The test database is independent of `compose.yaml`; stopping or recreating it
does not affect the application's local PostgreSQL database.

## Test PostgreSQL credentials

Docker initialization and JDBC test configuration deliberately use different
environment-variable names.

Before creating the test container:

```powershell
$env:REGIONAL_CONFIRMATION_TEST_POSTGRES_PASSWORD = "regional_core_banking_test"
```

For the JDBC integration test:

```powershell
$env:REGIONAL_CONFIRMATION_IT_DB_URL = "jdbc:postgresql://localhost:15433/regional_core_banking_test?currentSchema=core_banking"
$env:REGIONAL_CONFIRMATION_IT_DB_USERNAME = "regional_core_banking_test"
$env:REGIONAL_CONFIRMATION_IT_DB_PASSWORD = "regional_core_banking_test"
```
