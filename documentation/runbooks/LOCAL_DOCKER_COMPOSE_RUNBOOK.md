# Local Docker Compose Runbook

## Purpose
This runbook describes how to pull, start, inspect and stop the local Docker Compose environment for the Regional Core Banking API. The API image is published by GitHub Actions to Docker Hub and is not built by local Compose. This applies to local integration only and does not define production or authoritative Amplitude/Informix settings.

## Runtime services
| Service | Host port | Container port |
| --- | ---: | ---: |
| Regional Core Banking API | 9092 | 9092 |
| PostgreSQL technical database | 15434 | 5432 |
| Oracle customer mock | 1521 | 1521 |

Container-to-container PostgreSQL access uses `postgres:5432`, not host port `15434`.

## Prerequisites
- Docker Desktop running.
- Access to the Docker Hub repository `d22002/regional-core-banking-api`.
- Local `.env` containing required secrets, SMTP configuration and optionally an explicit `REGIONAL_API_IMAGE` tag.
- `.env` must remain uncommitted.
- Oracle JDBC is a standard runtime dependency; no Oracle-specific Maven profile is required.

## 1. Select the API image
By default, Compose uses:

```text
d22002/regional-core-banking-api:latest
```

For reproducible testing, set an immutable image tag in `.env`, for example:

```text
REGIONAL_API_IMAGE=d22002/regional-core-banking-api:sha-<commit>
```

`latest` is reserved for images published from `main`.

## 2. Pull Docker image
```powershell
docker compose pull regional-core-banking-api
```

Local Compose must not run `docker compose build` for the API service.

## 3. Start
```powershell
docker compose up -d
docker compose ps
```

Expected services:
```text
regional-core-banking-api
regional-core-banking-postgres
regional-customer-oracle
```

PostgreSQL and Oracle must become healthy before the API can complete startup.

## 4. Logs
API:
```powershell
docker compose logs -f regional-core-banking-api
```

Latest 100 API lines:
```powershell
docker compose logs --tail=100 regional-core-banking-api
```

PostgreSQL:
```powershell
docker compose logs -f postgres
```

Oracle:
```powershell
docker compose logs -f customer-oracle
```

All services:
```powershell
docker compose logs -f
```

`Ctrl+C` stops log following without stopping containers.

## 5. Verify API
Swagger UI:
```text
http://localhost:9092/swagger-ui.html
```

Implemented Springdoc groups:
```text
http://localhost:9092/v3/api-docs/customer
http://localhost:9092/v3/api-docs/payment-confirmation
http://localhost:9092/v3/api-docs/payment-execution
```

## 6. Stop
Normal shutdown, preserving volumes:
```powershell
docker compose down
```

Deliberate local data reset only:
```powershell
docker compose down -v
```

Do not use `-v` as the normal shutdown command.

## Troubleshooting

### Oracle JDBC driver
If startup reports `Failed to load driver class oracle.jdbc.OracleDriver`, verify the JAR:
```powershell
jar tf target/regional-core-banking-api-1.0.0-SNAPSHOT.jar | Select-String "ojdbc"
```

If the published image is missing the Oracle JDBC driver, do not rebuild it through
local Compose. Validate the CI artifact/build configuration, publish a corrected image,
then pull it again:

```powershell
docker compose pull regional-core-banking-api
docker compose up -d
```

### PostgreSQL
Docker profile URL:
```text
jdbc:postgresql://postgres:5432/regional_core_banking?currentSchema=core_banking
```
The `15434:5432` mapping is for connections from the developer machine.

### API port
Expected mapping:
```text
9092:9092
```

## Security notes
- Never commit `.env`.
- Never commit SMTP/database passwords, private keys, certificates or trust stores.
- Local Oracle and SMTP values are integration-test configuration only.
- Local configuration does not establish production banking, security or Amplitude/Informix settings.
