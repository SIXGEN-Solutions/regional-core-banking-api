# Lot 5A — élimination des abstractions préparatoires EVE

- Révision source : `feat/payment-orchestration-worflow @ c7821fc200a80c9a4bbe787931c59ebcafe6b84f`
- Portée : retrait d'un ensemble préparatoire non raccordé à l'exécution T0.
- Le script refuse toute référence active résiduelle à l'une des huit classes supprimées.
- Les mentions documentaires historiques sont conservées comme preuves datées.
- Aucune modification du chemin JDBC T0, OpenAPI, SQL, transaction ou configuration.

## Fichiers retirés et empreintes avant suppression

| Fichier | SHA-256 |
| --- | --- |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveAtomicEventNumberPort.java` | `c1b0bd06d7f1d82c946f6bd82d9bfdd00bd6804876d61e0843b253756ac864a7` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\UnconfiguredBkeveAtomicEventNumberPort.java` | `1abd8f93cef09ef001501940f4e9b18ba3be61d9dd394fecd783a7dee94c2651` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveEventNumberAllocationService.java` | `bbace36981541e805fa477026e809880a8e030519a21c9239afad855e38d751e` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveTransactionScopedAllocationPort.java` | `e9984d8fa7768b6f002ea174b33bef8e2d315504f9b9186bde69649e50cb6378` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\UnconfiguredBkeveTransactionScopedAllocationPort.java` | `39d355041a65dfbfcdffd3899e970c830e39cd6de6dd59cee0c7e420dfddd0eb` |
| `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveTransactionScopedAllocationService.java` | `6f060776b2711556fccedab7f4bf2a59504db43482a0d10236a99fc1e9a18b33` |
| `src\test\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveEventNumberAllocationServiceTest.java` | `dd14524a379c50710b94148be888aabef50ae8f0b58302e91a4147d05432836b` |
| `src\test\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveTransactionScopedAllocationServiceTest.java` | `7fa780e2d8d624f176ed4dc21a88e874a3df87db09cdc2416aa52bb54b2f8918` |

## Références historiques conservées

- `documentation/archive/r6/R6_6_5_BKEVE_EVE_ALLOCATION.md` : `BkeveAtomicEventNumberPort`
- `documentation/archive/r6/R6_6_5_BKEVE_EVE_ALLOCATION.md` : `BkeveEventNumberAllocationService`
- `documentation/archive/r6/R6_6_5_BKEVE_EVE_ALLOCATION.md` : `BkeveEventNumberAllocationServiceTest`
- `documentation/archive/r6/R6_6_5_BKEVE_EVE_ALLOCATION.md` : `UnconfiguredBkeveAtomicEventNumberPort`
- `documentation/archive/r6/R6_6_5_TRANSACTION_SCOPED_EVE_DESIGN.md` : `BkeveTransactionScopedAllocationPort`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `BkeveAtomicEventNumberPort`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `BkeveEventNumberAllocationService`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `BkeveTransactionScopedAllocationPort`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `BkeveTransactionScopedAllocationService`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `UnconfiguredBkeveAtomicEventNumberPort`
- `documentation/maintenance/CLEANUP_LOT1_REPORT.md` : `UnconfiguredBkeveTransactionScopedAllocationPort`
- `documentation/validation/R6_6_9_VALIDATION_REPORT.md` : `BkeveEventNumberAllocationServiceTest`
- `documentation/validation/R6_6_9_VALIDATION_REPORT.md` : `BkeveTransactionScopedAllocationServiceTest`

## Classes délibérément conservées

- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\JdbcBkeveTransactionCoordinator.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveTransactionWriteAssembly.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveJdbcPostingAdapter.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkevePostingAndBalanceWrite.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveIndicativeBalanceUpdate.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\BkeveExecutionBalanceUpdate.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\LegacyBkeveSql.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\LegacyBkeveIndicativeBalanceUpdate.java`
- `src\main\java\com\regional\corebanking\payment\infrastructure\amplitude\UnconfiguredPaymentExecutionBankingAdapter.java`

## Gates à vérifier

1. `git diff --check` et contrôle manuel du diff.
2. `./mvnw -Dtest=JdbcBkeveTransactionCoordinatorTest,BkeveTransactionWriteAssemblyTest,BkevePostingAndBalanceWriteTest,LegacyBkeveJdbcSqlTest test`.
3. `./mvnw clean verify`.
4. Tests PostgreSQL et Oracle opt-in séparément sur leurs environnements dédiés.
5. Confirmer qu'aucune exécution financière réelle n'a été déclenchée.
