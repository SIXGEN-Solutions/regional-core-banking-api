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
REGIONAL_API_IMAGE=d22002/regional-core-banking-api:<6-char-git-sha>
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

## R6.6.10 — Paiement simulé Oracle Docker + PostgreSQL de test

**Périmètre : intégration locale uniquement.** Les commandes de cette section
ne réalisent aucune opération sur Amplitude/Informix et ne constituent pas
une validation du schéma bancaire réel. Elles réutilisent le véritable adapter
`JdbcPaymentExecutionBankingAdapter` contre une base Oracle Free **synthétique**,
ainsi que la persistance technique PostgreSQL réelle dans une base **jetable**.

Cette procédure est distincte du lancement de l'image publiée décrit plus haut :
le test Oracle est exécuté par Maven sur les sources locales. Un `docker compose up`
standard ou le démarrage de l'API seule **ne lance pas** `OraclePaymentExecutionIT`.

### A. Vérifier que le correctif d'intégration Oracle a été appliqué

Les fichiers suivants doivent exister dans le workspace local :

- `docker/customer-oracle/002_payment_schema.sql` (fixture TEST ONLY) ;
- `src/test/java/com/regional/corebanking/payment/infrastructure/amplitude/OraclePaymentExecutionIT.java` ;
- `compose.customer-oracle.yml` doit monter `002_payment_schema.sql`, en plus de `001_customer_schema.sql`.

Au SHA de référence `18fe5c7bd0ac949f139186fc9a93331b9401b75d`, ces
modifications n'existent pas encore sur GitHub. Si elles ne sont pas présentes
localement, appliquer d'abord le correctif local `apply_r6610_oracle_payment.py`
fourni séparément ; **ne pas exécuter le test avant cette étape**. Ce runbook
n'applique ni la fixture ni les modifications Java.

```powershell
git branch --show-current
git rev-parse HEAD
Test-Path .\docker\customer-oracle\002_payment_schema.sql
Test-Path .\src\test\java\com\regional\corebanking\payment\infrastructure\amplitude\OraclePaymentExecutionIT.java
Select-String -Path .\compose.customer-oracle.yml -Pattern '002_payment_schema.sql'
```

Les deux commandes `Test-Path` doivent retourner `True` et `Select-String`
doit trouver le montage. La vérification Git permet d'enregistrer la révision
réellement testée ; elle ne remplace pas l'inspection des modifications locales.

### B. Démarrer les bases de test isolées (PowerShell)

Depuis la racine du repository, dans une session PowerShell :

```powershell
$env:ORACLE_APP_PASSWORD = '<mot-de-passe-local-de-test>'
$env:REGIONAL_CONFIRMATION_TEST_POSTGRES_PASSWORD = '<mot-de-passe-postgres-test>'

docker compose -f compose.customer-oracle.yml up -d
docker compose -f compose.test-postgres.yml up -d

docker compose -f compose.customer-oracle.yml ps
docker compose -f compose.test-postgres.yml ps
```

Attendre que les services `customer-oracle` et `test-postgres` soient
`healthy`. Si besoin :

```powershell
docker compose -f compose.customer-oracle.yml logs --tail=100 customer-oracle
docker compose -f compose.test-postgres.yml logs --tail=100 test-postgres
```

**Volume Oracle existant :** `container-entrypoint-initdb.d` n'est exécuté
qu'à la première initialisation du volume Oracle. Si `customer-oracle-data`
existait avant l'ajout de `002_payment_schema.sql`, **ne pas conclure** que
la fixture Payment a été créée. Examiner la présence des tables dans le mock ;
si nécessaire, appliquer explicitement la fixture dans cette instance de test
ou recréer **uniquement** son volume jetable après contrôle des données.
Ne jamais exécuter un `down -v` sur une stack partagée ou contenant des données
à conserver. Le runbook ne prescrit aucune suppression automatique de volume.

### C. Configurer les variables de test (même session PowerShell)

```powershell
$env:RUN_R6610_ORACLE_IT = 'true'
$env:ORACLE_JDBC_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:REGIONAL_CONFIRMATION_IT_DB_URL = 'jdbc:postgresql://localhost:15433/regional_core_banking_test?currentSchema=core_banking'
$env:REGIONAL_CONFIRMATION_IT_DB_USERNAME = 'regional_core_banking_test'
$env:REGIONAL_CONFIRMATION_IT_DB_PASSWORD = $env:REGIONAL_CONFIRMATION_TEST_POSTGRES_PASSWORD
```

Le test PostgreSQL détruit/recrée `regional_payment_execution` et
`api_eodprocess` dans **`regional_core_banking_test` uniquement**. Il contient
une garde refusant tout autre nom de base. Les fixtures Oracle sont également
réinitialisées par le test. Ne jamais utiliser une base partagée, la base
technique runtime `regional_core_banking`, ou une instance Oracle bancaire réelle.

### D. Exécuter les validations dans l'ordre

Test d'intégration JDBC avec Oracle Free + PostgreSQL jetable :

```powershell
.\mvnw.cmd '-Dtest=OraclePaymentExecutionIT' test
```

Le test doit démontrer, sur le véritable adapter bancaire Spring Java :

| Scénario | Contrôles attendus |
| --- | --- |
| Paiement réussi | Résultat `COMPLETED`, une ligne BKEVE, 233 positions JDBC traitées par le mapper, soldes débiteur/créditeur mis à jour, `OPE/EVE` persisté dans PostgreSQL |
| Idempotence et recovery | Même clé sans second INSERT, lecture Oracle de l'événement par référence de paiement et clé d'idempotence |
| Fonds insuffisants | `REJECTED`, aucun BKEVE créé, aucune réservation EVE |
| Échec d'écriture de solde | Rollback Oracle (pas de ligne BKEVE committée), `UNKNOWN` technique, réservation EVE conservée dans PostgreSQL |

Les résultats ci-dessus sont **attendus**, et non attestés tant que le test
n'a pas terminé avec succès. Le jeu de tests R6.6.10 actuel traverse
`JdbcPaymentExecutionBankingAdapter.execute()` mais ne constitue pas encore
un test HTTP MockMvc traversant l'endpoint Payment Execution.

Puis gates généraux (à exécuter localement, jamais lancés par ce runbook) :

```powershell
.\mvnw.cmd clean verify
git diff --check
git status --short
```

Ne pas confondre `clean verify` vert et résultat du test opt-in : si la variable
`RUN_R6610_ORACLE_IT` est absente, JUnit **désactive** le test Oracle.
Conserver les logs de chaque commande et vérifier qu'aucun test ciblé n'a été
skippé. Un test qui échoue ne doit pas être présenté comme validation passée.

### E. Observation et diagnostic

Vérifier les traces Maven/JUnit du test, puis les journaux Docker :

```powershell
docker compose -f compose.customer-oracle.yml logs --tail=100 customer-oracle
docker compose -f compose.test-postgres.yml logs --tail=100 test-postgres
```

Les assertions Java sont la vérification primaire des données BKEVE, des
soldes, de la réservation `API_EODProcess` et de l'idempotence. Ne pas déduire
une exécution bancaire validée de la seule ligne PostgreSQL : la preuve
simulée de postage est la ligne BKEVE dans Oracle Free.

En cas d'erreur `ORA-00942` ou `ORA-00904`, vérifier en premier lieu que le
script `002_payment_schema.sql` a réellement été appliqué sur la base
Oracle mock. En cas d'erreur PostgreSQL, confirmer le nom exact
`regional_core_banking_test`, le port `15433`, l'utilisateur et le mot de passe.
Si les tests échouent pour des raisons de dialecte SQL, **ne pas modifier** le
SQL Amplitude de production à partir du seul comportement Oracle mock.

### F. Arrêter sans détruire les volumes

```powershell
docker compose -f compose.test-postgres.yml down
docker compose -f compose.customer-oracle.yml down
```

Le conteneur PostgreSQL de test utilise `tmpfs` ; ses données sont volatiles.
Le volume Oracle persiste après `down` (sans `-v`). Aucun service de production
n'est concerné par ces commandes, à condition de cibler uniquement ces fichiers
Compose de test.

### Critère de sortie R6.6.10

**GO Spring Boot simulé** seulement si les tests ciblés réellement exécutés
sont verts, les assertions PostgreSQL/Oracle satisfaites, et les anomalies
résiduelles documentées. **Ce GO n'est pas un GO d'écriture Amplitude réelle.**
Les contrôles sur un environnement bancaire autorisé restent couverts par
`documentation/runbooks/R6_6_10_CONTROLLED_ACTIVATION.md`.
