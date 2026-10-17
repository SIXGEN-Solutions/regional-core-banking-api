# R6.6.4 — Enrichissement bancaire préparatoire BKEVE

**Statut : IMPLEMENTATION_PREPARATORY / BANK_EVIDENCE_PENDING. Aucun postage autorisé.**

## Révision et sources

- Repository : `SIXGEN-Solutions/regional-core-banking-api`.
- Base examinée : `feat/payment-orchestration-worflow @ c20702d16674d3fbefa7cf25cc8e6e1079fa566e`.
- R6.5 : `PaymentExecutionBankingContext` et `PaymentExecutionBankingContextResolver` existants.
- R6.6.3 : `BkeveJdbcColumnOrder` et `BkeveJdbcParameterMapper` existants.
- Matrice : `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv` (legacy, classification non assimilée à une approbation bancaire exhaustive).
- SIXPAY est uniquement une référence de compatibilité, aucune classe SIXPAY copiée.

## Contrat interne du service

`BkeveBankEnrichmentService.enrich(context, validatedBankValues)` constitue une étape interne **non exécutante** :

- `EVE` reçoit `PaymentExecutionBankingContext.eventNumber()` ; ce service n'attribue, ne verrouille ni ne réserve le numéro.
- `DCO` reçoit `accountingDate()`.
- `DVA1` et `DVA2` reçoivent les dates de valeur du contexte R6.5.
- Les dates restent des `LocalDate` dans la carte préparatoire ; leur conversion JDBC requiert la preuve physique du schéma.
- Les autres colonnes ne sont conservées que si la banque les fournit explicitement ; aucune valeur legacy ou devise `001` n'est générée par défaut.
- Les champs fournis par le client (AGSA, AGE, OPE, MHT1, MON1, MHT2, MON2, LIB1, CSP1) restent alimentés par R6.6.3 et ne peuvent être redéfinis dans la carte bancaire.
- Le résultat est immuable et partiel. Son passage à `BkeveJdbcParameterMapper.map` reste bloqué tant que les 233 colonnes ne sont pas explicitement renseignées.

## Ce qui reste bloquant

- R6.5 utilise actuellement `MAX(eve)` suivi d'un contrôle d'existence : **ce n'est pas une attribution atomique prouvée**. Ne pas activer l'insertion bancaire à partir de ce numéro.
- Les données nécessaires à `DEV1/DEV2`, `CLI1/CLI2`, `NOM1/NOM2`, `GES1/GES2`, `SOL1/SOL2`, `SUF1/SUF2` et autres champs ne sont pas toutes disponibles dans l'adaptateur bancaire actuel ; il faut des sources et projections bancaires approuvées pour les deux comptes.
- La relation entre `XAF` et un code devise Amplitude, la validation de la même agence, la politique de valeurs nulles, les dates JDBC, les champs de traitement (`DOU`, `ETA`, `UTI`, etc.) et l'ensemble des valeurs par défaut legacy demandent encore une validation explicite.
- Le schéma physique, l'atomicité d'exécution et les règles de récupération sont hors de ce sous-lot.
- L'adaptateur R6 actuel demeure fail-closed et ne poste aucun BKEVE.

## Validation à lancer localement par l'utilisateur

```bash
./mvnw -Dtest=BkeveBankEnrichmentServiceTest,BkeveJdbcParameterMapperTest test
./mvnw clean verify
git diff --check
git status --short
```

Aucun gate, commit, push, PR, modification SIXPAY ou écriture en base n'est effectué par ce script.
