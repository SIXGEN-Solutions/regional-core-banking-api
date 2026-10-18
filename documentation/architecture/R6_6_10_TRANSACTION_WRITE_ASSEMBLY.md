# R6.6 — Assemblage des composants BKEVE existants (complément sans modification)

**Statut : PREPARATORY / BLOCKED_BANK_EVIDENCE — NO FINANCIAL ACTIVATION.**

- Repository : `SIXGEN-Solutions/regional-core-banking-api`
- Branche / SHA de départ vérifié : `feat/payment-orchestration-worflow @ f9509dc971722a1550015597ffd9c479db8a171d`
- Mode : consultation GitHub lecture seule ; script Python local additif.
- SIXPAY : `REFERENCE_ONLY`, aucun changement.

## Périmètre ajouté

`BkeveTransactionWriteAssembly` connecte **sans les modifier** :
`BkeveBankEnrichmentService` -> `BkeveJdbcParameterMapper` (233 positions)
-> `BkevePostingAndBalanceWrite` (INSERT puis UPDATE soldes).

Il vérifie d'abord que l'EVE attribué par le coordinateur est identique à
l'EVE du contexte bancaire ; refuse une table autre que BKEVE/BKEVE_EOD ;
et exige que toutes les 233 valeurs soient résolues explicitement avant SQL.
L'appelant doit employer `JdbcBkeveTransactionCoordinator.allocateAndWrite`,
qui détient la même connexion/transaction pendant ces opérations.

Les tests Mockito démontrent une composition transactionnelle jour/nuit, un
refus pré-INSERT si les données manquent, un refus d'EVE discordant et un
rollback lors d'une erreur de soldes. Ils ne prouvent aucune règle Amplitude.

## Ce qui reste NON implémentable de façon sûre à partir des sources actuelles

1. Valeurs bancaires approuvées pour toutes les positions non transportées dans
   le contrat, conversion JDBC physique et INSERT final des 233 colonnes.
2. SQL et règles métier des soldes indicatifs jour/nuit ; le code actuel ne
   contient qu'un port explicite qui échoue sans implémentation bancaire.
3. Allocation EVE mono-instance **face à tous les producteurs externes** : un
   verrou JVM n'empêche pas les écritures effectuées hors de l'API.
4. Corrélation durable OPE/EVE/DCO et lookup Informix autoritatif pour résoudre
   les issues financières incertaines, notamment les pannes autour de COMMIT.
5. Activation de la route réelle : `JdbcPaymentExecutionBankingAdapter.execute`
   demeure fail-closed jusqu'aux validations de La Régionale.
6. Tests réels sur un environnement Informix jetable/autorisé avec SQL et fixtures
   confirmés, y compris concurrence et rollback.

Les fichiers déjà existants, le contrat OpenAPI, le schéma PostgreSQL, SIXPAY,
CI, sécurité et configuration de production ne sont pas modifiés.

## Gates à lancer localement (non exécutés ici)

```bash
./mvnw -Dtest=BkeveTransactionWriteAssemblyTest test
./mvnw -Dtest=JdbcBkeveTransactionCoordinatorTest,BkevePostingAndBalanceWriteTest,BkeveJdbcPostingAdapterTest,BkeveJdbcParameterMapperTest test
./mvnw clean verify
git diff --check
git status --short
```

Aucun GO financier : les décisions de banque et les tests réels restent bloquants.
