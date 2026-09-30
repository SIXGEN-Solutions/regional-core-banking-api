# Local Docker Compose Runbook

## Purpose
This runbook describes how to build, start, inspect and stop the local Docker Compose environment for the Regional Core Banking API. It applies to local integration only and does not define production or authoritative Amplitude/Informix settings.

## Runtime services
| Service | Host port | Container port |
| --- | ---: | ---: |
| Regional Core Banking API | 9092 | 9092 |
| PostgreSQL technical database | 15434 | 5432 |
| Oracle customer mock | 1521 | 1521 |

Container-to-container PostgreSQL access uses `postgres:5432`, not host port `15434`.

## Prerequisites
- Docker Desktop running.
- Java 21 and Maven available.
- Local `.env` containing required secrets and SMTP configuration.
- `.env` must remain uncommitted.
- Oracle JDBC is a standard runtime dependency; no Oracle-specific Maven profile is required.

## 1. Build
```powershell
mvn clean package -DskipTests
```

Optional Oracle driver verification:
```powershell
jar tf target/regional-core-banking-api-1.0.0-SNAPSHOT.jar | Select-String "ojdbc"
```

Expected entry similar to:
```text
BOOT-INF/lib/ojdbc11-23.26.1.0.0.jar
```

Do not combine `clean` with `r3-no-openapi-generation` unless generated OpenAPI sources are supplied by another approved mechanism.

## 2. Build Docker image
```powershell
docker compose build regional-core-banking-api
```

Force a full rebuild when required:
```powershell
docker compose build --no-cache regional-core-banking-api
```

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

Then rebuild:
```powershell
mvn clean package -DskipTests
docker compose build --no-cache regional-core-banking-api
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
