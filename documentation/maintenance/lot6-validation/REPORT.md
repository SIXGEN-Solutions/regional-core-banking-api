# Validation finale — assainissement Regional Core Banking API

- SHA de référence : `77798dba552bd4a1fb08e2328e6cc1cdf726d6fe`
- Branche : `feat/payment-orchestration-worflow`
- Date UTC : `2026-10-18T16:54:28.562470+00:00`
- Environnement : exécution locale, tests sur bases jetables uniquement

| Gate | Résultat | Détail |
| --- | --- | --- |
| java-version | PASS | exit=0; `documentation\maintenance\lot6-validation\java-version.log` |
| maven-targeted | FAIL | exit=1; voir `documentation\maintenance\lot6-validation\maven-targeted.log` |

## Vérifications structurelles

- Présent : `src/test/java/com/regional/corebanking/payment/infrastructure/amplitude/BkeveCurrentSqlAndBalanceTest.java`
- Présent : `documentation/architecture/PAYMENT_EXECUTION_AND_RECOVERY.md`
- Présent : `documentation/architecture/PAYMENT_EXECUTION_DECISIONS_AND_EVIDENCE.md`
- Présent : `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv`
- Présent : `contracts/openapi/regional-core-banking-api-v1.yaml`

## Réserve financière

Un test Oracle Free / PostgreSQL local ne vaut pas homologation Informix / Amplitude.
Aucun résultat `UNKNOWN` ne doit conduire à un rejeu aveugle.
Les gates non exécutés ou ignorés empêchent l'attestation complète du lot 6.
