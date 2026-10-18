# Lot 5B — Retrait du code de caractérisation Legacy

Révision source : `feat/payment-orchestration-worflow @ 7725ee21c29f2b01051a3af2fa4ea6cb0415e2df`.

Les tests du chemin courant ont réussi avant suppression. Les anciens scénarios ne sont pas totalement sémantiquement identiques : la décision de mutation de solde dépend désormais de BkeveExecutionBalanceUpdate.

| Fichier supprimé | SHA-256 |
| --- | --- |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\LegacyBkeveSql.java` | `f6f3a44bb853b9b83f94009a7cc3d75ec0e89b49075e5204c9f032f541f5c275` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\LegacyBkeveIndicativeBalanceUpdate.java` | `9c4055f2f28b81c35203a5c91b231f5783c73238725d841dd4f3480aa7f42203` |
| `src\test\java\com\regional\corebanking\payment\infrastructure\amplitude\LegacyBkeveJdbcSqlTest.java` | `db3e59378d535ca0e60c345ba2ae4f736f1bd9b92777038511475544dda38c8c` |

Gates restants : `./mvnw clean verify`, intégrations opt-in, revue du diff.
